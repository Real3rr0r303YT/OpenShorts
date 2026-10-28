package org.openshorts.data.peertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal, dependency-free PeerTube API client.
 *
 * Requests are sent only to the instance the user configured. No cookies, no
 * account, no identifiers — just anonymous, read-only HTTPS calls.
 */
class PeerTubeClient(private val userAgent: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Videos of an instance.
     * @param sort PeerTube sort key, e.g. `-publishedAt` (newest) or `-trending`.
     * @param localOnly only videos uploaded to this instance (no federated ones).
     */
    suspend fun videos(
        instanceUrl: String,
        start: Int,
        count: Int,
        sort: String = "-publishedAt",
        localOnly: Boolean = false,
        tagsOneOf: List<String> = emptyList(),
    ): List<Video> {
        val tags = tagsOneOf.joinToString("") { "&tagsOneOf=${it.urlEncode()}" }
        return fetchVideos(
            instanceUrl,
            "$instanceUrl/api/v1/videos?start=$start&count=$count&sort=$sort&nsfw=false&skipCount=true" +
                tags + if (localOnly) "&isLocal=true" else "",
        )
    }

    /** Full-text video search (title, description, tags) on the instance. */
    suspend fun searchVideos(instanceUrl: String, query: String, start: Int, count: Int): List<Video> =
        fetchVideos(
            instanceUrl,
            "$instanceUrl/api/v1/search/videos?search=${query.urlEncode()}&start=$start&count=$count" +
                "&sort=-match&nsfw=false",
        )

    /** Videos of one channel, newest first. [handle] is `name` or `name@host`. */
    suspend fun channelVideos(instanceUrl: String, handle: String, start: Int, count: Int): List<Video> =
        fetchVideos(
            instanceUrl,
            "$instanceUrl/api/v1/video-channels/${handle.urlEncode()}/videos?start=$start&count=$count" +
                "&sort=-publishedAt&nsfw=false&skipCount=true",
        )

    /** Channels (creators) matching [query]. */
    suspend fun searchCreators(instanceUrl: String, query: String, count: Int = 10): List<Creator> =
        withContext(Dispatchers.IO) {
            val url = "$instanceUrl/api/v1/search/video-channels?search=${query.urlEncode()}&start=0&count=$count"
            json.decodeFromString<ChannelSearchResponse>(get(url)).data.map { it.toCreator(instanceUrl) }
        }

    private suspend fun fetchVideos(instanceUrl: String, url: String): List<Video> =
        withContext(Dispatchers.IO) {
            json.decodeFromString<VideoListResponse>(get(url)).data.map { it.toVideo(instanceUrl) }
        }

    /** Resolves a playable stream: HLS playlist if available, otherwise a progressive file. */
    suspend fun streamUrl(instanceUrl: String, uuid: String): String? =
        withContext(Dispatchers.IO) {
            val details = json.decodeFromString<VideoDetails>(get("$instanceUrl/api/v1/videos/$uuid"))
            details.streamingPlaylists.firstNotNullOfOrNull { it.playlistUrl }
                ?: selectBestFile(details.files + details.streamingPlaylists.flatMap { it.files })?.fileUrl
        }

    /** Top-level comments of a video, newest first. */
    suspend fun comments(instanceUrl: String, uuid: String, start: Int, count: Int): CommentPage =
        withContext(Dispatchers.IO) {
            val url = "$instanceUrl/api/v1/videos/$uuid/comment-threads?start=$start&count=$count&sort=-createdAt"
            val response = json.decodeFromString<CommentThreadsResponse>(get(url))
            CommentPage(
                total = response.total,
                comments = response.data.filterNot { it.isDeleted }.map { it.toComment(instanceUrl) },
            )
        }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}

/** Highest resolution up to [maxHeight], or the smallest available file if all are larger. */
fun selectBestFile(files: List<VideoFile>, maxHeight: Int = 720): VideoFile? {
    val playable = files.filter { !it.fileUrl.isNullOrBlank() && (it.resolution?.id ?: 0) > 0 }
    return playable.filter { it.resolution!!.id <= maxHeight }.maxByOrNull { it.resolution!!.id }
        ?: playable.minByOrNull { it.resolution!!.id }
}

internal fun PeerTubeVideo.toVideo(instanceUrl: String): Video {
    val actor = account ?: channel
    // Prefer the channel's avatar (what TikTok-style UIs show), fall back to the account.
    val avatarActor = channel?.takeIf { it.avatarPath() != null } ?: account
    return Video(
        uuid = uuid,
        title = name,
        channelName = channel?.displayName?.ifBlank { null } ?: actor?.displayName.orEmpty(),
        accountHandle = actor?.handle().orEmpty(),
        thumbnailUrl = (previewPath ?: thumbnailPath)?.let { "$instanceUrl$it" },
        durationSeconds = duration,
        views = views,
        likes = likes,
        comments = comments,
        isLive = isLive,
        watchUrl = "$instanceUrl/w/${shortUUID ?: uuid}",
        creatorAvatarUrl = avatarActor?.avatarPath()?.let { "$instanceUrl$it" },
        creatorUrl = channel?.url ?: account?.url,
        channelHandle = channel?.let { it.name + if (it.host.isNotEmpty()) "@${it.host}" else "" },
        instanceUrl = instanceUrl,
    )
}

internal fun PeerTubeActor.toCreator(instanceUrl: String): Creator = Creator(
    handle = name + if (host.isNotEmpty()) "@$host" else "",
    displayName = displayName.ifBlank { name },
    avatarUrl = avatarPath()?.let { "$instanceUrl$it" },
    url = url,
    followers = followersCount,
)

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")

internal fun PeerTubeComment.toComment(instanceUrl: String): Comment = Comment(
    id = id,
    text = htmlToPlainText(text),
    authorName = account?.displayName?.ifBlank { null } ?: account?.name.orEmpty(),
    authorHandle = account?.handle().orEmpty(),
    authorAvatarUrl = account?.avatarPath()?.let { "$instanceUrl$it" },
    createdAt = createdAt,
    replies = totalReplies,
)

private fun PeerTubeActor.handle(): String = "@$name" + if (host.isNotEmpty()) "@$host" else ""

/** Smallest avatar that is still sharp at ~48dp, or the legacy single avatar. */
private fun PeerTubeActor.avatarPath(): String? =
    (avatars.filter { it.path != null && it.width >= 96 }.minByOrNull { it.width } ?: avatars.firstOrNull { it.path != null })
        ?.path ?: avatar?.path

/** PeerTube comments are (sanitized) HTML; the feed only needs plain text. */
fun htmlToPlainText(html: String): String =
    html.replace(Regex("(?i)<br\\s*/?>|</p>\\s*<p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")
        .trim()
