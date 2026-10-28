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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openshorts.OpenShortsApp
import org.openshorts.data.peertube.Video

data class FeedUiState(
    /** Incremented whenever the feed source changes, so the pager can reset. */
    val generation: Int = 0,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    val playingUuid: String? = null,
    val isBuffering: Boolean = false,
    val userPaused: Boolean = false,
    val playbackFailed: Boolean = false,
)

private data class FeedSource(val instanceUrl: String, val shortsOnly: Boolean)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenShortsApp
    private val client = app.peerTubeClient

    /** One shared player for the whole feed; only the visible page renders it. */
    val player: ExoPlayer = buildPlayer(application)

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var source: FeedSource? = null
    private var nextStart = 0
    private var loadJob: Job? = null
    private var playJob: Job? = null
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
            app.settingsRepository.settings
                .map { FeedSource(it.instanceUrl, it.shortsOnly) }
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

    private fun reset(newSource: FeedSource) {
        source = newSource
        nextStart = 0
        streamCache.clear()
        loadJob?.cancel()
        playJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.update { FeedUiState(generation = it.generation + 1) }
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
                    val page = client.latestVideos(src.instanceUrl, nextStart, PAGE_SIZE)
                    nextStart += PAGE_SIZE
                    pages++
                    end = page.size < PAGE_SIZE
                    batch += page.filter { video ->
                        !video.isLive && (!src.shortsOnly || video.durationSeconds in 1..SHORT_MAX_SECONDS)
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
            val url = streamCache[video.uuid]
                ?: runCatching { client.streamUrl(src.instanceUrl, video.uuid) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                    ?.also { streamCache[video.uuid] = it }
            if (url == null) {
                _state.update { it.copy(playbackFailed = true) }
                return@launch
            }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun togglePlayPause() {
        val paused = !_state.value.userPaused
        _state.update { it.copy(userPaused = paused) }
        if (paused) player.pause() else player.play()
    }

    /** Lifecycle pause (app in background, settings opened) – keeps the user's choice. */
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
