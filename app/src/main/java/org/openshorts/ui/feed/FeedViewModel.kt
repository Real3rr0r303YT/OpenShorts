package org.openshorts.ui.feed

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.SearchQuery
import org.openshorts.data.TECH_TAGS
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.Video

/** The tabs on top of the feed. [TECH] (cybersecurity, Linux, programming…) is the default. */
enum class FeedTab(val sort: String, val localOnly: Boolean, val tags: List<String> = emptyList()) {
    TECH("-publishedAt", false, TECH_TAGS),
    TRENDING("-trending", false),
    LATEST("-publishedAt", false),
    LOCAL("-publishedAt", true),
}

/** What the feed is currently showing. */
sealed interface FeedContent {
    data class Tab(val tab: FeedTab) : FeedContent

    data class Search(val query: SearchQuery) : FeedContent

    /** A fixed list, e.g. the user's liked or saved videos, starting at [startIndex]. */
    data class Collection(val title: String, val videos: List<Video>, val startIndex: Int) : FeedContent
}

data class FeedUiState(
    /** Incremented whenever the feed source changes, so the pager can reset. */
    val generation: Int = 0,
    val content: FeedContent = FeedContent.Tab(FeedTab.TECH),
    /** Page the pager should start on (non-zero for collections). */
    val startPage: Int = 0,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    val playingUuid: String? = null,
    val isBuffering: Boolean = false,
    val userPaused: Boolean = false,
    val playbackFailed: Boolean = false,
)

private data class FeedSource(val instanceUrl: String, val shortsOnly: Boolean, val content: FeedContent)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val client = app.peerTubeClient
    private val profiles = app.profileRepository

    /** One shared player for the whole feed; only the visible page renders it. */
    val player: ExoPlayer = buildPlayer(application)

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** UUIDs of videos the user liked (stored locally on the device). */
    val likedIds: StateFlow<Set<String>> = profiles.likedVideos
        .map { likes -> likes.mapTo(HashSet()) { it.uuid } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** UUIDs of bookmarked videos. */
    val savedIds: StateFlow<Set<String>> = profiles.savedVideos
        .map { saved -> saved.mapTo(HashSet()) { it.uuid } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Handles of followed creators. */
    val followedHandles: StateFlow<Set<String>> = profiles.following
        .map { creators -> creators.mapTo(HashSet()) { it.handle } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The instance currently shown, used for the comments sheet. */
    val instanceUrl: String? get() = source?.instanceUrl

    private val selectedContent = MutableStateFlow<FeedContent>(FeedContent.Tab(FeedTab.TECH))
    private var source: FeedSource? = null
    private var nextStart = 0
    private var loadJob: Job? = null
    private var playJob: Job? = null
    private var preloadJob: Job? = null
    private val streamCache = HashMap<String, String>()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(playbackFailed = true, isBuffering = false) }
            }
        })

        viewModelScope.launch {
            combine(app.settingsRepository.settings, selectedContent) { settings, content ->
                FeedSource(settings.instanceUrl, settings.shortsOnly, content)
            }
                .distinctUntilChanged()
                .collect { reset(it) }
        }
        viewModelScope.launch {
            app.settingsRepository.settings
                .map { it.loopVideos }
                .distinctUntilChanged()
                .collect { loop ->
                    player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }
        }
    }

    fun selectTab(tab: FeedTab) {
        selectedContent.value = FeedContent.Tab(tab)
    }

    fun search(query: SearchQuery) {
        selectedContent.value = FeedContent.Search(query)
    }

    /** Plays a fixed list of videos (liked, saved, …) starting at [startIndex]. */
    fun playCollection(title: String, videos: List<Video>, startIndex: Int) {
        if (videos.isEmpty()) return
        selectedContent.value = FeedContent.Collection(title, videos, startIndex.coerceIn(videos.indices))
    }

    /** Leaves search / collection mode and returns to the default tab. */
    fun clearSearch() {
        selectedContent.value = FeedContent.Tab(FeedTab.TECH)
    }

    /** Pull-to-refresh / tapping the active tab again. */
    fun refresh() {
        source?.let { reset(it) }
    }

    private fun reset(newSource: FeedSource) {
        source = newSource
        nextStart = 0
        streamCache.clear()
        loadJob?.cancel()
        playJob?.cancel()
        preloadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        val content = newSource.content
        if (content is FeedContent.Collection) {
            _state.update {
                FeedUiState(
                    generation = it.generation + 1,
                    content = content,
                    videos = content.videos,
                    startPage = content.startIndex,
                    endReached = true,
                )
            }
            return
        }
        _state.update { FeedUiState(generation = it.generation + 1, content = content) }
        loadMore()
    }

    fun retry() = loadMore()

    fun loadMore() {
        val src = source ?: return
        val current = _state.value
        if (current.isLoading || current.endReached) return
        _state.update { it.copy(isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
            try {
                val batch = mutableListOf<Video>()
                var end = false
                var pages = 0
                // With "shorts only" a single page may contain few matches, so fetch a few.
                while (batch.size < MIN_BATCH && !end && pages < MAX_PAGES_PER_LOAD) {
                    val page = fetchPage(src, nextStart)
                    nextStart += PAGE_SIZE
                    pages++
                    end = page.size < PAGE_SIZE
                    // Search results are shown unfiltered: the user asked for them explicitly.
                    val applyShortsFilter = src.shortsOnly && src.content is FeedContent.Tab
                    batch += page.filter { video ->
                        !video.isLive && (!applyShortsFilter || video.durationSeconds in 1..SHORT_MAX_SECONDS)
                    }
                }
                _state.update {
                    it.copy(
                        videos = (it.videos + batch).distinctBy(Video::uuid),
                        isLoading = false,
                        endReached = end,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private suspend fun fetchPage(src: FeedSource, start: Int): List<Video> =
        when (val content = src.content) {
            is FeedContent.Tab -> client.videos(
                src.instanceUrl, start, PAGE_SIZE,
                sort = content.tab.sort, localOnly = content.tab.localOnly, tagsOneOf = content.tab.tags,
            )
            is FeedContent.Search -> when (val query = content.query) {
                is SearchQuery.Text -> client.searchVideos(src.instanceUrl, query.text, start, PAGE_SIZE)
                is SearchQuery.Tag -> client.videos(src.instanceUrl, start, PAGE_SIZE, tagsOneOf = listOf(query.tag))
                is SearchQuery.Channel -> client.channelVideos(src.instanceUrl, query.handle, start, PAGE_SIZE)
            }
            is FeedContent.Collection -> emptyList()
        }

    /** Called when the pager settles on [index]. */
    fun onPageSelected(index: Int) {
        val src = source ?: return
        val videos = _state.value.videos
        val video = videos.getOrNull(index) ?: return
        if (index >= videos.size - PRELOAD_THRESHOLD) loadMore()

        if (_state.value.playingUuid == video.uuid && player.mediaItemCount > 0) {
            if (!_state.value.userPaused) player.play()
            return
        }

        playJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.update { it.copy(playingUuid = video.uuid, userPaused = false, playbackFailed = false) }

        playJob = viewModelScope.launch {
            val url = resolveStream(video.instanceUrl.ifEmpty { src.instanceUrl }, video.uuid)
            if (url == null) {
                _state.update { it.copy(playbackFailed = true) }
                return@launch
            }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
            preloadNext(src, index)
        }
    }

    /** Resolves the next video's stream URL in advance so swiping feels instant. */
    private fun preloadNext(src: FeedSource, index: Int) {
        val next = _state.value.videos.getOrNull(index + 1) ?: return
        if (next.uuid in streamCache) return
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch { resolveStream(next.instanceUrl.ifEmpty { src.instanceUrl }, next.uuid) }
    }

    private suspend fun resolveStream(instanceUrl: String, uuid: String): String? =
        streamCache[uuid]
            ?: runCatching { client.streamUrl(instanceUrl, uuid) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?.also { streamCache[uuid] = it }

    fun togglePlayPause() {
        val paused = !_state.value.userPaused
        _state.update { it.copy(userPaused = paused) }
        if (paused) player.pause() else player.play()
    }

    fun toggleLike(video: Video) {
        viewModelScope.launch { profiles.toggleLike(video) }
    }

    /** Double-tap: only ever adds a like, like in other short-video apps. */
    fun like(video: Video) {
        if (video.uuid !in likedIds.value) toggleLike(video)
    }

    fun toggleSaved(video: Video) {
        viewModelScope.launch { profiles.toggleSaved(video) }
    }

    fun toggleFollow(video: Video) {
        val creator = video.toCreator() ?: return
        viewModelScope.launch { profiles.toggleFollow(creator) }
    }

    /** Lifecycle pause (app in background, other tab opened) – keeps the user's choice. */
    fun pausePlayback() = player.pause()

    fun resumePlayback() {
        if (!_state.value.userPaused && player.mediaItemCount > 0) player.play()
    }

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val MIN_BATCH = 8
        const val MAX_PAGES_PER_LOAD = 4
        const val PRELOAD_THRESHOLD = 3
        const val SHORT_MAX_SECONDS = 180
    }
}

/** The creator of [video] as a followable [Creator], if the channel is known. */
fun Video.toCreator(): Creator? {
    val handle = channelHandle ?: accountHandle.removePrefix("@").ifBlank { null } ?: return null
    return Creator(handle = handle, displayName = channelName, avatarUrl = creatorAvatarUrl, url = creatorUrl)
}

@OptIn(UnstableApi::class)
private fun buildPlayer(application: Application): ExoPlayer {
    val userAgent = (application as OpenShortsApp).userAgent
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(false)
    val dataSourceFactory = DefaultDataSource.Factory(application, httpFactory)
    return ExoPlayer.Builder(application)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}
