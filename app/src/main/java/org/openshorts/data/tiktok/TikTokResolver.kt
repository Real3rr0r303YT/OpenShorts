package org.openshorts.data.tiktok

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openshorts.data.peertube.PLATFORM_TIKTOK
import org.openshorts.data.peertube.TIKTOK_UUID_PREFIX
import org.openshorts.data.peertube.Video
import org.openshorts.data.peertube.urlEncoded
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class TikTokOEmbed(
    val title: String? = null,
    @kotlinx.serialization.SerialName("author_name") val authorName: String? = null,
    @kotlinx.serialization.SerialName("author_url") val authorUrl: String? = null,
    @kotlinx.serialization.SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

/**
 * Turns shared TikTok links into feed videos. TikTok offers no public discovery API, so the TikTok
 * feed plays links the user shares to OpenShorts or pastes. Metadata comes from TikTok's public
 * oEmbed endpoint; playback uses the official embed player.
 */
class TikTokResolver(private val userAgent: String, private val json: Json) {

    /** Resolves a TikTok URL (incl. short links like vm.tiktok.com) to a [Video]. */
    suspend fun resolve(link: String): Video = withContext(Dispatchers.IO) {
        val url = findTikTokUrl(link) ?: throw IOException("No TikTok link")
        val canonical = videoIdFrom(url)?.let { url } ?: followRedirects(url)
        val id = videoIdFrom(canonical) ?: throw IOException("No TikTok video id")
        val meta = runCatching { oEmbed(canonical) }.getOrNull()
        Video(
            uuid = TIKTOK_UUID_PREFIX + id,
            title = meta?.title.orEmpty(),
            channelName = meta?.authorName.orEmpty(),
            accountHandle = meta?.authorUrl?.substringAfterLast('/')?.takeIf { it.startsWith("@") }
                ?: meta?.authorName?.let { "@$it" }.orEmpty(),
            thumbnailUrl = meta?.thumbnailUrl,
            durationSeconds = 0,
            views = 0,
            likes = 0,
            watchUrl = canonical.substringBefore('?'),
            creatorUrl = meta?.authorUrl,
            aspectRatio = 9f / 16f,
            platform = PLATFORM_TIKTOK,
        )
    }

    private fun oEmbed(url: String): TikTokOEmbed {
        val connection = open("https://www.tiktok.com/oembed?url=${url.urlEncoded()}", follow = true)
        try {
            if (connection.responseCode !in 200..299) throw IOException("oEmbed ${connection.responseCode}")
            return json.decodeFromString(TikTokOEmbed.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun followRedirects(url: String): String {
        val connection = open(url, follow = true)
        try {
            connection.responseCode
            return connection.url.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, follow: Boolean): HttpURLConnection {
        if (!url.startsWith("https://")) throw IOException("Only HTTPS is allowed")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = follow
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", userAgent)
        }
    }
}

private val TIKTOK_URL = Regex("""https://(?:www\.|m\.|vm\.|vt\.)?tiktok\.com/[^\s]+""", RegexOption.IGNORE_CASE)
private val VIDEO_ID = Regex("""/(?:video|v|embed/v2|embed)/(\d{8,25})""")

/** Finds the first TikTok URL in shared text ("Look at this! https://vm.tiktok.com/ZM…/"). */
fun findTikTokUrl(text: String): String? = TIKTOK_URL.find(text)?.value?.trimEnd('.', ',', ')', '!')

/** The numeric video id of a full TikTok video URL, or null for short links. */
fun videoIdFrom(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)
