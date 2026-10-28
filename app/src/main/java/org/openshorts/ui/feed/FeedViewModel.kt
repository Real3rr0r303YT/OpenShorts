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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.SHORT_CLIP_MAX_SECONDS
import org.openshorts.data.SearchQuery
import org.openshorts.data.TrendCategory
import org.openshorts.data.peertube.YOUTUBE_UUID_PREFIX
import org.openshorts.data.youtube.YouTubeCursor
import org.openshorts.data.TECH_TAGS
import org.openshorts.data.peertube.Creator
import org.openshorts.data.peertube.Video
import org.openshorts.data.rankShorts

/** The tabs on top of the feed. [FOR_YOU] (hot short clips) is the default. */
enum class FeedTab(
    val sort: String,
    val localOnly: Boolean,
    val tags: List<String> = emptyList(),
    /** Only shown when logged in with a PeerTube account. */
    val requiresLogin: Boolean = false,
) {
    /** "For You": PeerTube's "hot" ranking, short vertical clips first. */
    FOR_YOU("-hot", false),
    TECH("-publishedAt", false, TECH_TAGS),

    /** Videos of channels the user follows on their PeerTube account. */
    FOLLOWING("-publishedAt", false, requiresLogin = true),
    TRENDING("-trending", false),
    LATEST("-publishedAt", false),
    LOCAL("-publishedAt", true),
}

/** The two content sources offered in the feed header. */
enum class FeedPlatform { PEERTUBE, YOUTUBE }

/** What the feed is currently showing. */
sealed interface FeedContent {
    data class Tab(val tab: FeedTab) : FeedContent

    /**
     * YouTube Shorts via Piped/Invidious: a [category], a free [query], or – with both null –
     * the "For You" mix that rotates through several Shorts searches.
     */
    data class YouTube(val category: TrendCategory? = null, val query: String? = null) : FeedContent

    data class Search(val query: SearchQuery) : FeedContent

    /** A fixed list, e.g. the user's liked or saved videos, starting at [startIndex]. */
    data class Collection(val title: String, val videos: List<Video>, val startIndex: Int) : FeedContent
}

data class FeedUiState(
    /** Incremented whenever the feed source changes, so the pager can reset. */
    val generation: Int = 0,
    val content: FeedContent = FeedContent.Tab(FeedTab.FOR_YOU),
    /** Page the pager should start on (non-zero for collections). */
    val startPage: Int = 0,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    /** Video on screen and the pool player that plays it. */
    val playingUuid: String? = null,
    val activePlayer: Int = 0,
    /** Next video, already buffered (first frame ready) on the other pool player. */
    val preloadedUuid: String? = null,
    val isBuffering: Boolean = false,
    val userPaused: Boolean = false,
    val playbackFailed: Boolean = false,
    /** No Piped/Invidious instance could deliver results right now. */
    val youTubeUnavailable: Boolean = false,
)

private data class FeedSource(val instanceUrl: String, val shortsOnly: Boolean, val content: FeedContent)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val client = app.peerTubeClient
    private val profiles = app.profileRepository
    private val accounts = app.accountRepository
    private val youTube = app.youTubeClient

    /** Whether the "YouTube Shorts" source is enabled in the settings. */
    val youTubeEnabled: StateFlow<Boolean> = app.settingsRepository.settings
        .map { it.youTubeEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** True while logged in with a PeerTube account (likes etc. are then synced). */
    val loggedIn: StateFlow<Boolean> = accounts.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _syncErrors = MutableSharedFlow<String?>(extraBufferCapacity = 4)

    /** Emits when an action could not be sent to the PeerTube server (message may be null). */
    val syncErrors: SharedFlow<String?> = _syncErrors.asSharedFlow()

    /**
     * Two players take turns: one plays the current video, the other already buffers the next one.
     * When the user swipes, the buffered player simply starts – no black frame, no loading spinner.
     */
    private val players: List<ExoPlayer> = List(2) { buildPlayer(application) }

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

    private val selectedContent = MutableStateFlow<FeedContent>(FeedContent.Tab(FeedTab.FOR_YOU))
    private var source: FeedSource? = null
    private var nextStart = 0
    private var loadJob: Job? = null
    private var playJob: Job? = null
    private var preloadJob: Job? = null
    private val streamCache = HashMap<String, String>()

    /** Category searches fall back to a short-clip text search when the tags find nothing. */
    private var categoryFallback = false

    // YouTube pagination: one cursor per query; the "For You" mix rotates through the queries.
    private val ytCursors = HashMap<String, YouTubeCursor>()
    private val ytExhausted = HashSet<String>()
    private var ytRotation = 0
    private var emptyLoads = 0

    private val activePlayer: ExoPlayer get() = players[_state.value.activePlayer]
    private val standbyIndex: Int get() = 1 - _state.value.activePlayer

    init {
        players.forEachIndexed { index, player ->
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (index == _state.value.activePlayer) {
                        _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (index == _state.value.activePlayer) {
                        _state.update { it.copy(playbackFailed = true, isBuffering = false) }
                    } else {
                        _state.update { it.copy(preloadedUuid = null) }
                    }
                }
            })
        }

        viewModelScope.launch {
            combine(app.settingsRepository.settings, selectedContent) { settings, content ->
                val allowed = if (content is FeedContent.YouTube && !settings.youTubeEnabled) {
                    FeedContent.Tab(FeedTab.FOR_YOU)
                } else {
                    content
                }
                FeedSource(settings.instanceUrl, settings.shortsOnly, allowed)
            }
                .distinctUntilChanged()
                .collect { reset(it) }
        }
        viewModelScope.launch {
            app.settingsRepository.settings
                .map { it.loopVideos }
                .distinctUntilChanged()
                .collect { loop ->
                    players.forEach { it.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
                }
        }
    }

    /** The pool player showing [uuid] (playing or buffered), or null. */
    fun playerFor(uuid: String, state: FeedUiState): Player? = when (uuid) {
        state.playingUuid -> players[state.activePlayer]
        state.preloadedUuid -> players[1 - state.activePlayer]
        else -> null
    }

    /** The player of the video on screen, for the progress bar. */
    fun currentPlayer(state: FeedUiState): Player = players[state.activePlayer]

    fun selectTab(tab: FeedTab) {
        selectedContent.value = FeedContent.Tab(tab)
    }

    /** Switches between the PeerTube network and the YouTube Shorts feed. */
    fun selectPlatform(platform: FeedPlatform) {
        selectedContent.value = when (platform) {
            FeedPlatform.PEERTUBE -> FeedContent.Tab(FeedTab.FOR_YOU)
            FeedPlatform.YOUTUBE -> FeedContent.YouTube()
        }
    }

    fun selectYouTubeCategory(category: TrendCategory?) {
        selectedContent.value = FeedContent.YouTube(category = category)
    }

    /** The platform the feed currently shows; searches go to the same platform. */
    val platform: FeedPlatform
        get() = if (_state.value.content.isYouTube) FeedPlatform.YOUTUBE else FeedPlatform.PEERTUBE

    fun search(query: SearchQuery) {
        selectedContent.value = if (platform == FeedPlatform.YOUTUBE) {
            when (query) {
                is SearchQuery.Category -> FeedContent.YouTube(category = query.category)
                is SearchQuery.Tag -> FeedContent.YouTube(query = query.tag)
                is SearchQuery.Channel -> FeedContent.YouTube(query = query.handle.substringBefore('@'))
                is SearchQuery.Text -> FeedContent.YouTube(query = query.text)
            }
        } else {
            FeedContent.Search(query)
        }
    }

    /** Opens a creator's videos on the platform the creator belongs to. */
    fun openCreator(creator: Creator) {
        selectedContent.value = if (creator.handle.startsWith(YOUTUBE_UUID_PREFIX)) {
            FeedContent.YouTube(query = creator.displayName)
        } else {
            FeedContent.Search(SearchQuery.Channel(creator.handle))
        }
    }

    /** Plays a fixed list of videos (liked, saved, …) starting at [startIndex]. */
    fun playCollection(title: String, videos: List<Video>, startIndex: Int) {
        if (videos.isEmpty()) return
        selectedContent.value = FeedContent.Collection(title, videos, startIndex.coerceIn(videos.indices))
    }

    /** Leaves search / collection mode and returns to the default tab of the current platform. */
    fun clearSearch() {
        selectedContent.value = if (platform == FeedPlatform.YOUTUBE) FeedContent.YouTube() else FeedContent.Tab(FeedTab.FOR_YOU)
    }

    /** Pull-to-refresh / tapping the active tab again. */
    fun refresh() {
        source?.let { reset(it) }
    }

    private fun reset(newSource: FeedSource) {
        source = newSource
        nextStart = 0
        categoryFallback = false
        emptyLoads = 0
        ytCursors.clear()
        ytExhausted.clear()
        ytRotation = 0
        streamCache.clear()
        loadJob?.cancel()
        playJob?.cancel()
        preloadJob?.cancel()
        players.forEach {
            it.stop()
            it.clearMediaItems()
        }
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
        _state.update { it.copy(isLoading = true, error = null, youTubeUnavailable = false) }

        loadJob = viewModelScope.launch {
            try {
                val batch = mutableListOf<Video>()
                var end = false
                var pages = 0
                // A page may contain few short clips, so fetch a few pages per load.
                while (batch.size < MIN_BATCH && !end && pages < MAX_PAGES_PER_LOAD) {
                    val yt = src.content as? FeedContent.YouTube
                    val page = if (yt != null) {
                        fetchYouTube(yt).also { end = it.second }.first
                    } else {
                        fetchPage(src, nextStart).also { end = it.size < PAGE_SIZE }
                    }
                    nextStart += PAGE_SIZE
                    pages++
                    batch += rankShorts(page, shortsOnly = src.shortsOnly)
                    val content = src.content
                    if (end && batch.isEmpty() && content is FeedContent.Search &&
                        content.query is SearchQuery.Category && !categoryFallback
                    ) {
                        // No tagged clips: search the category name as text instead.
                        categoryFallback = true
                        nextStart = 0
                        end = false
                    }
                }
                _state.update {
                    it.copy(
                        videos = (it.videos + batch).distinctBy(Video::uuid),
                        isLoading = false,
                        endReached = end,
                    )
                }
                // Keep loading (a few times) until something can be shown, otherwise the spinner would never end.
                emptyLoads = if (_state.value.videos.isEmpty()) emptyLoads + 1 else 0
                if (_state.value.videos.isEmpty() && !end) {
                    if (emptyLoads < MAX_EMPTY_LOADS) loadMore() else _state.update { it.copy(endReached = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (src.content is FeedContent.YouTube) {
                    // Never show raw parser/network errors for the volunteer-run YouTube proxies.
                    _state.update { it.copy(isLoading = false, youTubeUnavailable = true) }
                } else {
                    _state.update { it.copy(isLoading = false, error = e.message ?: e.javaClass.simpleName) }
                }
            }
        }
    }

    /** Fetches the next YouTube page; returns the videos and whether nothing more can be loaded. */
    private suspend fun fetchYouTube(content: FeedContent.YouTube): Pair<List<Video>, Boolean> {
        val queries = youTubeQueries(content)
        val open = queries.filter { it !in ytExhausted }
        if (open.isEmpty()) return emptyList<Video>() to true
        val query = open[ytRotation++ % open.size]
        val page = youTube.searchShorts(query, ytCursors[query])
        val next = page.next
        if (next == null) ytExhausted += query else ytCursors[query] = next
        return page.videos to (ytExhausted.size == queries.size)
    }

    private suspend fun fetchPage(src: FeedSource, start: Int): List<Video> {
        // Searches ask the server for short clips directly when "shorts only" is on.
        val maxDuration = if (src.shortsOnly) SHORT_CLIP_MAX_SECONDS else null
        return when (val content = src.content) {
            is FeedContent.Tab -> if (content.tab == FeedTab.FOLLOWING) {
                accounts.subscriptionVideos(start, PAGE_SIZE)
            } else {
                client.videos(
                    src.instanceUrl, start, PAGE_SIZE,
                    sort = content.tab.sort, localOnly = content.tab.localOnly, tagsOneOf = content.tab.tags,
                )
            }
            is FeedContent.Search -> when (val query = content.query) {
                is SearchQuery.Text -> client.searchVideos(src.instanceUrl, query.text, start, PAGE_SIZE, maxDuration)
                is SearchQuery.Tag -> client.videos(src.instanceUrl, start, PAGE_SIZE, tagsOneOf = listOf(query.tag))
                is SearchQuery.Category -> if (categoryFallback) {
                    client.searchVideos(src.instanceUrl, query.category.label, start, PAGE_SIZE, maxDuration)
                } else {
                    client.videos(src.instanceUrl, start, PAGE_SIZE, sort = "-publishedAt", tagsOneOf = query.category.tags)
                }
                is SearchQuery.Channel -> client.channelVideos(src.instanceUrl, query.handle, start, PAGE_SIZE)
            }
            is FeedContent.Collection, is FeedContent.YouTube -> emptyList()
        }
    }

    /** Called when the pager settles on [index]. */
    fun onPageSelected(index: Int) {
        val src = source ?: return
        val state = _state.value
        val videos = state.videos
        val video = videos.getOrNull(index) ?: return
        if (index >= videos.size - PRELOAD_THRESHOLD) loadMore()

        if (state.playingUuid == video.uuid && activePlayer.mediaItemCount > 0) {
            if (!state.userPaused) activePlayer.play()
            preloadNext(src, index)
            return
        }

        playJob?.cancel()
        preloadJob?.cancel()
        val previous = activePlayer

        if (state.preloadedUuid == video.uuid) {
            // Swap: the standby player already has this video buffered – start instantly.
            val next = standbyIndex
            previous.pause()
            _state.update {
                it.copy(
                    playingUuid = video.uuid,
                    activePlayer = next,
                    preloadedUuid = null,
                    userPaused = false,
                    playbackFailed = false,
                    isBuffering = players[next].playbackState == Player.STATE_BUFFERING,
                )
            }
            players[next].seekTo(0)
            players[next].playWhenReady = true
            preloadNext(src, index)
            return
        }

        previous.stop()
        previous.clearMediaItems()
        _state.update {
            it.copy(playingUuid = video.uuid, preloadedUuid = null, userPaused = false, playbackFailed = false)
        }
        playJob = viewModelScope.launch {
            val url = resolveStream(video.instanceUrl.ifEmpty { src.instanceUrl }, video.uuid)
            if (url == null) {
                _state.update { it.copy(playbackFailed = true) }
                return@launch
            }
            activePlayer.setMediaItem(MediaItem.fromUri(url))
            activePlayer.prepare()
            activePlayer.playWhenReady = true
            preloadNext(src, index)
        }
    }

    /** Buffers the next video on the standby player so the next swipe starts instantly. */
    private fun preloadNext(src: FeedSource, index: Int) {
        val next = _state.value.videos.getOrNull(index + 1) ?: return
        if (_state.value.preloadedUuid == next.uuid) return
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            val url = resolveStream(next.instanceUrl.ifEmpty { src.instanceUrl }, next.uuid) ?: return@launch
            val standby = players[standbyIndex]
            standby.playWhenReady = false
            standby.setMediaItem(MediaItem.fromUri(url))
            standby.prepare()
            _state.update { it.copy(preloadedUuid = next.uuid) }
        }
    }

    private suspend fun resolveStream(instanceUrl: String, uuid: String): String? {
        val video = _state.value.videos.firstOrNull { it.uuid == uuid }
        return streamCache[uuid]
            ?: runCatching {
                if (video?.isYouTube == true) youTube.streamUrl(video) else client.streamUrl(instanceUrl, uuid)
            }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?.also { streamCache[uuid] = it }
    }

    fun togglePlayPause() {
        val paused = !_state.value.userPaused
        _state.update { it.copy(userPaused = paused) }
        if (paused) activePlayer.pause() else activePlayer.play()
    }

    fun toggleLike(video: Video) {
        val like = video.uuid !in likedIds.value
        viewModelScope.launch {
            profiles.toggleLike(video)
            if (!video.isYouTube) syncToServer { accounts.setLiked(video, like) }
        }
    }

    /** Double-tap: only ever adds a like, like in other short-video apps. */
    fun like(video: Video) {
        if (video.uuid !in likedIds.value) toggleLike(video)
    }

    fun toggleSaved(video: Video) {
        val save = video.uuid !in savedIds.value
        viewModelScope.launch {
            profiles.toggleSaved(video)
            if (!video.isYouTube) syncToServer { accounts.setSaved(video, save) }
        }
    }

    fun toggleFollow(video: Video) {
        val creator = video.toCreator() ?: return
        val follow = creator.handle !in followedHandles.value
        viewModelScope.launch {
            profiles.toggleFollow(creator)
            // YouTube creators are followed locally only.
            if (!video.isYouTube) syncToServer { accounts.setFollowing(creator, follow) }
        }
    }

    /** Sends an interaction to the user's PeerTube server when logged in; the local state is kept either way. */
    private suspend fun syncToServer(action: suspend () -> Unit) {
        if (!accounts.isLoggedIn) return
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncErrors.tryEmit(e.message)
        }
    }

    /** Lifecycle pause (app in background, other tab opened) – keeps the user's choice. */
    fun pausePlayback() = activePlayer.pause()

    fun resumePlayback() {
        if (!_state.value.userPaused && activePlayer.mediaItemCount > 0) activePlayer.play()
    }

    override fun onCleared() {
        players.forEach { it.release() }
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val MIN_BATCH = 8
        const val MAX_PAGES_PER_LOAD = 4
        const val PRELOAD_THRESHOLD = 3
        const val MAX_EMPTY_LOADS = 3
    }
}

/** YouTube searches; the "For You" mix rotates through several Shorts topics. */
internal fun youTubeQueries(content: FeedContent.YouTube): List<String> = when {
    content.query != null -> listOf("${content.query} #shorts")
    content.category != null -> listOf("${content.category.label} #shorts")
    else -> listOf("#shorts") + TrendCategory.entries.filter { it != TrendCategory.SHORTS }.map { "${it.label} #shorts" }
}

/** True for content that comes from YouTube. */
val FeedContent.isYouTube: Boolean
    get() = this is FeedContent.YouTube ||
        (this is FeedContent.Collection && videos.isNotEmpty() && videos.all { it.isYouTube })

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
    // Short clips: start playing after a small buffer instead of the default 2.5 s.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 5_000,
            /* maxBufferMs = */ 30_000,
            /* bufferForPlaybackMs = */ 700,
            /* bufferForPlaybackAfterRebufferMs = */ 1_500,
        )
        .build()
    return ExoPlayer.Builder(application)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(loadControl)
        .build()
}
