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

    /** Newest videos of an instance (local and federated), newest first. */
    suspend fun latestVideos(instanceUrl: String, start: Int, count: Int): List<Video> =
        withContext(Dispatchers.IO) {
            val url = "$instanceUrl/api/v1/videos?start=$start&count=$count" +
                "&sort=-publishedAt&nsfw=false&skipCount=true"
            json.decodeFromString<VideoListResponse>(get(url)).data.map { it.toVideo(instanceUrl) }
        }

    /** Resolves a playable stream: HLS playlist if available, otherwise a progressive file. */
    suspend fun streamUrl(instanceUrl: String, uuid: String): String? =
        withContext(Dispatchers.IO) {
            val details = json.decodeFromString<VideoDetails>(get("$instanceUrl/api/v1/videos/$uuid"))
            details.streamingPlaylists.firstNotNullOfOrNull { it.playlistUrl }
                ?: selectBestFile(details.files + details.streamingPlaylists.flatMap { it.files })?.fileUrl
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
    val host = actor?.host.orEmpty()
    return Video(
        uuid = uuid,
        title = name,
        channelName = channel?.displayName?.ifBlank { null } ?: actor?.displayName.orEmpty(),
        accountHandle = if (actor == null) "" else "@${actor.name}" + if (host.isNotEmpty()) "@$host" else "",
        thumbnailUrl = (previewPath ?: thumbnailPath)?.let { "$instanceUrl$it" },
        durationSeconds = duration,
        views = views,
        likes = likes,
        isLive = isLive,
        watchUrl = "$instanceUrl/w/${shortUUID ?: uuid}",
    )
}
