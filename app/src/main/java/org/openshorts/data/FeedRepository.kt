package org.openshorts.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.openshorts.data.peertube.PeerTubeClient
import org.openshorts.data.peertube.Video
import org.openshorts.data.youtube.YouTubeClient
import org.openshorts.data.youtube.YouTubeCursor
import org.openshorts.data.youtube.YouTubePage
import org.openshorts.data.youtube.YouTubeUnavailableException
import org.openshorts.data.youtube.YouTubeWebExtractor

/**
 * Where the feed gets its content and playable streams from, per platform:
 * - PeerTube: the instance API,
 * - YouTube: Piped/Invidious proxies first; if they all fail and the user allowed it,
 *   the [YouTubeWebExtractor] reads m.youtube.com directly,
 * - TikTok: no stream – those videos play in the official embed player.
 */
class FeedRepository(
    private val peerTube: PeerTubeClient,
    private val youTubeProxies: YouTubeClient,
    private val youTubeWeb: YouTubeWebExtractor,
    private val settings: SettingsRepository,
) {

    private suspend fun directAllowed(): Boolean = settings.settings.first().youTubeDirect

    /** YouTube Shorts search with proxy → direct fallback. */
    suspend fun searchYouTube(query: String, cursor: YouTubeCursor?): YouTubePage =
        try {
            youTubeProxies.searchShorts(query, cursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: YouTubeUnavailableException) {
            if (!directAllowed()) throw e
            youTubeWeb.searchShorts(query)
        }

    /** A playable stream URL for [video], or null if it cannot be played (the feed then skips it). */
    suspend fun resolveStream(video: Video, fallbackInstance: String): String? {
        if (video.isTikTok) return null
        if (!video.isYouTube) return peerTube.streamUrl(video.instanceUrl.ifEmpty { fallbackInstance }, video.uuid)
        val direct = directAllowed()
        // Videos found on m.youtube.com are resolved there first.
        if (direct && video.instanceUrl == YouTubeWebExtractor.BASE) {
            tryOrNull { youTubeWeb.streamUrl(video.youTubeId) }?.let { return it }
        }
        tryOrNull { youTubeProxies.streamUrl(video) }?.let { return it }
        return if (direct) tryOrNull { youTubeWeb.streamUrl(video.youTubeId) } else null
    }

    private suspend fun <T> tryOrNull(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
