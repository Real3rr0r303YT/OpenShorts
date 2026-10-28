package org.openshorts.data.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.openshorts.data.peertube.Comment
import org.openshorts.data.peertube.CommentPage
import org.openshorts.data.peertube.Http
import org.openshorts.data.peertube.PLATFORM_YOUTUBE
import org.openshorts.data.peertube.Video
import org.openshorts.data.peertube.YOUTUBE_UUID_PREFIX
import org.openshorts.data.peertube.htmlToPlainText
import org.openshorts.data.peertube.urlEncoded
import java.io.IOException

/** A page of YouTube results plus the cursor for the next page (bound to the backend that produced it). */
data class YouTubePage(val videos: List<Video>, val next: YouTubeCursor?)

data class YouTubeCursor(val backend: YouTubeBackend, val token: String)

/** A privacy-friendly YouTube front end; OpenShorts never talks to YouTube/Google directly. */
sealed interface YouTubeBackend {
    val base: String

    data class Piped(override val base: String) : YouTubeBackend

    data class Invidious(override val base: String) : YouTubeBackend
}

/**
 * YouTube Shorts via public Piped and Invidious instances.
 *
 * Search results, thumbnails and video streams are requested from (and proxied by) these
 * instances. Public instances come and go, so the client tries them in order and remembers
 * the last one that worked.
 */
class YouTubeClient(private val http: Http, private val json: Json) {

    private val backends: List<YouTubeBackend> =
        PIPED_INSTANCES.map { YouTubeBackend.Piped(it) } + INVIDIOUS_INSTANCES.map { YouTubeBackend.Invidious(it) }

    @Volatile
    private var preferred: YouTubeBackend? = null

    /** Searches short videos for [query]; [cursor] continues a previous search. */
    suspend fun searchShorts(query: String, cursor: YouTubeCursor?): YouTubePage =
        if (cursor != null) {
            searchOn(cursor.backend, query, cursor.token)
        } else {
            withAnyBackend { searchOn(it, query, null) }
        }

    /** A directly playable stream URL (muxed MP4 or HLS) served through the proxy. */
    suspend fun streamUrl(video: Video): String? {
        val start = backends.firstOrNull { it.base == video.instanceUrl }
        return withAnyBackend(start) { backend ->
            when (backend) {
                is YouTubeBackend.Piped -> pipedStream(backend, video.youTubeId)
                is YouTubeBackend.Invidious -> invidiousStream(backend, video.youTubeId)
            } ?: throw IOException("No playable stream")
        }
    }

    suspend fun comments(video: Video): CommentPage {
        val start = backends.firstOrNull { it.base == video.instanceUrl }
        return withAnyBackend(start) { backend ->
            val comments = when (backend) {
                is YouTubeBackend.Piped -> {
                    val response: PipedComments = json.decodeFromString(get("${backend.base}/comments/${video.youTubeId}"))
                    response.comments.mapIndexed { index, c ->
                        Comment(
                            id = index.toLong(),
                            text = htmlToPlainText(c.commentText),
                            authorName = c.author.removePrefix("@"),
                            authorHandle = c.author,
                            authorAvatarUrl = c.thumbnail,
                            createdAt = "",
                            replies = c.replyCount,
                        )
                    }
                }
                is YouTubeBackend.Invidious -> {
                    val response: InvidiousComments = json.decodeFromString(get("${backend.base}/api/v1/comments/${video.youTubeId}"))
                    response.comments.mapIndexed { index, c ->
                        Comment(
                            id = index.toLong(),
                            text = c.content,
                            authorName = c.author.removePrefix("@"),
                            authorHandle = c.author,
                            authorAvatarUrl = c.authorThumbnails.lastOrNull()?.url?.let { absolute(backend, it) },
                            createdAt = "",
                            replies = 0,
                        )
                    }
                }
            }
            CommentPage(total = comments.size, comments = comments)
        }
    }

    // --- Piped ------------------------------------------------------------------------------------

    private suspend fun searchOn(backend: YouTubeBackend, query: String, token: String?): YouTubePage = when (backend) {
        is YouTubeBackend.Piped -> {
            val url = if (token == null) {
                "${backend.base}/search?q=${query.urlEncoded()}&filter=videos"
            } else {
                "${backend.base}/nextpage/search?nextpage=${token.urlEncoded()}&q=${query.urlEncoded()}&filter=videos"
            }
            val response: PipedSearchResponse = json.decodeFromString(get(url))
            YouTubePage(
                videos = response.items.filter { it.type == "stream" }.mapNotNull { it.toVideo(backend) },
                next = response.nextpage?.let { YouTubeCursor(backend, it) },
            )
        }
        is YouTubeBackend.Invidious -> {
            val page = token?.toIntOrNull() ?: 1
            val url = "${backend.base}/api/v1/search?q=${query.urlEncoded()}&type=video&duration=short&page=$page"
            val items = json.decodeFromString(ListSerializer(InvidiousVideoItem.serializer()), get(url))
            YouTubePage(
                videos = items.filter { it.type == "video" }.map { it.toVideo(backend) },
                next = if (items.isEmpty()) null else YouTubeCursor(backend, (page + 1).toString()),
            )
        }
    }

    private suspend fun pipedStream(backend: YouTubeBackend.Piped, id: String): String? {
        val streams: PipedStreams = json.decodeFromString(get("${backend.base}/streams/$id"))
        // Prefer a muxed (audio+video) MP4 up to 720p; otherwise the HLS playlist.
        val muxed = streams.videoStreams.filter { !it.videoOnly && it.url.isNotBlank() }
        val best = muxed.filter { it.height in 1..720 }.maxByOrNull { it.height } ?: muxed.firstOrNull()
        return best?.url ?: streams.hls
    }

    private fun PipedItem.toVideo(backend: YouTubeBackend.Piped): Video? {
        val id = url.substringAfter("v=", "").substringBefore('&').ifBlank { return null }
        val channelId = uploaderUrl?.substringAfterLast('/')
        return Video(
            uuid = YOUTUBE_UUID_PREFIX + id,
            title = title,
            channelName = uploaderName.orEmpty(),
            accountHandle = uploaderName?.let { "@$it" }.orEmpty(),
            thumbnailUrl = thumbnail,
            durationSeconds = duration.toInt(),
            views = views,
            likes = 0,
            watchUrl = "https://www.youtube.com/shorts/$id",
            creatorAvatarUrl = uploaderAvatar,
            creatorUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
            channelHandle = channelId?.let { YOUTUBE_UUID_PREFIX + it },
            instanceUrl = backend.base,
            // Piped marks Shorts explicitly; those are vertical.
            aspectRatio = if (isShort) 9f / 16f else null,
            platform = PLATFORM_YOUTUBE,
        )
    }

    // --- Invidious -----------------------------------------------------------------------------

    private suspend fun invidiousStream(backend: YouTubeBackend.Invidious, id: String): String? {
        // local=true makes the instance proxy the stream instead of linking to Google.
        val video: InvidiousVideo = json.decodeFromString(get("${backend.base}/api/v1/videos/$id?local=true"))
        val mp4 = video.formatStreams.filter { it.type.startsWith("video/mp4") && it.url.isNotBlank() }
            .maxByOrNull { it.resolution?.removeSuffix("p")?.toIntOrNull()?.takeIf { r -> r <= 720 } ?: 0 }
        return (mp4?.url ?: video.hlsUrl)?.let { absolute(backend, it) }
    }

    private fun InvidiousVideoItem.toVideo(backend: YouTubeBackend.Invidious): Video = Video(
        uuid = YOUTUBE_UUID_PREFIX + videoId,
        title = title,
        channelName = author,
        accountHandle = "@$author",
        thumbnailUrl = (
            videoThumbnails.firstOrNull { it.quality == "high" } ?: videoThumbnails.firstOrNull()
            )?.url?.let { absolute(backend, it) },
        durationSeconds = lengthSeconds.toInt(),
        views = viewCount,
        likes = 0,
        watchUrl = "https://www.youtube.com/shorts/$videoId",
        creatorUrl = "https://www.youtube.com/channel/$authorId",
        channelHandle = YOUTUBE_UUID_PREFIX + authorId,
        instanceUrl = backend.base,
        platform = PLATFORM_YOUTUBE,
    )

    // --- Helpers ---------------------------------------------------------------------------------

    private fun absolute(backend: YouTubeBackend, url: String): String = when {
        url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        else -> backend.base + url
    }

    private suspend fun get(url: String): String = http.request(url, timeoutMs = 12_000).body

    /** Runs [block] on the preferred backend first, then on the others until one succeeds. */
    private suspend fun <T> withAnyBackend(start: YouTubeBackend? = null, block: suspend (YouTubeBackend) -> T): T {
        val order = listOfNotNull(start, preferred).distinct() + backends.filter { it != start && it != preferred }
        var last: Exception? = null
        for (backend in order) {
            try {
                return block(backend).also { preferred = backend }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("No YouTube proxy reachable")
    }

    companion object {
        /** Public Piped API instances (see https://github.com/TeamPiped/documentation). */
        val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.private.coffee",
        )

        /** Public Invidious instances (see https://api.invidious.io). */
        val INVIDIOUS_INSTANCES = listOf(
            "https://invidious.nerdvpn.de",
            "https://inv.nadeko.net",
            "https://yewtu.be",
        )
    }
}
