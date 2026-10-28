package org.openshorts.data.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.openshorts.data.peertube.Http
import org.openshorts.data.peertube.PLATFORM_YOUTUBE
import org.openshorts.data.peertube.Video
import org.openshorts.data.peertube.YOUTUBE_UUID_PREFIX
import org.openshorts.data.peertube.urlEncoded

/**
 * Reads YouTube Shorts directly from the mobile website, without Piped/Invidious.
 *
 * - Discovery: the search results page embeds its data as `ytInitialData` (video ids, titles, channels).
 * - Playback: a Shorts page embeds `ytInitialPlayerResponse` with `streamingData`; we use its HLS
 *   manifest or a muxed MP4 that has a plain `url`.
 *
 * Streams protected by YouTube's signature cipher are deliberately **not** decrypted: those videos
 * return `null` and the feed skips them. Only used when the user enables "Load directly from
 * YouTube" (Google then sees the user's IP address).
 */
class YouTubeWebExtractor(private val http: Http, private val json: Json) {

    /** First page of Shorts for [query] (the mobile site has no simple pagination). */
    suspend fun searchShorts(query: String): YouTubePage {
        val url = "$BASE/results?search_query=${query.urlEncoded()}&sp=$SHORT_FILTER"
        val data = parseInitialJson(fetchPage(url), "ytInitialData") ?: return YouTubePage(emptyList(), null)
        return YouTubePage(collectVideos(data).distinctBy { it.uuid }, next = null)
    }

    /** A playable stream (HLS or muxed MP4 with a plain URL), or null if none is available. */
    suspend fun streamUrl(videoId: String): String? {
        val player = parseInitialJson(fetchPage("$BASE/shorts/$videoId"), "ytInitialPlayerResponse") ?: return null
        val status = player.path("playabilityStatus", "status")?.string()
        if (status != null && status != "OK") return null
        val streaming = player["streamingData"] as? JsonObject ?: return null
        streaming["hlsManifestUrl"]?.string()?.let { return it }
        val formats = (streaming["formats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it["url"] != null && it["mimeType"]?.string()?.startsWith("video/mp4") == true }
        return formats
            .filter { (it["height"]?.string()?.toIntOrNull() ?: 0) <= 720 }
            .maxByOrNull { it["height"]?.string()?.toIntOrNull() ?: 0 }
            ?.get("url")?.string()
    }

    private suspend fun fetchPage(url: String): String =
        http.request(
            url,
            headers = mapOf(
                "User-Agent" to MOBILE_USER_AGENT,
                "Accept" to "text/html",
                "Accept-Language" to "en-US,en;q=0.8",
                // Skips the EU cookie consent interstitial (no tracking cookies are accepted).
                "Cookie" to "SOCS=CAI; CONSENT=PENDING+987",
            ),
            timeoutMs = 12_000,
        ).body

    private fun parseInitialJson(html: String, variable: String): JsonObject? =
        extractJsonObject(html, variable)?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /** Walks `ytInitialData` and collects Shorts and short videos from the known renderer types. */
    private fun collectVideos(root: JsonElement): List<Video> {
        val result = mutableListOf<Video>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    (element["reelItemRenderer"] as? JsonObject)?.let { r ->
                        video(
                            id = r["videoId"]?.string(),
                            title = r.text("headline"),
                            channel = null,
                            thumbnail = r.lastThumbnail("thumbnail"),
                            seconds = SHORT_UNKNOWN_LENGTH,
                            vertical = true,
                        )?.let(result::add)
                    }
                    (element["shortsLockupViewModel"] as? JsonObject)?.let { r ->
                        video(
                            id = r.path("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId")?.string(),
                            title = r.path("overlayMetadata", "primaryText", "content")?.string(),
                            channel = null,
                            thumbnail = ((r.path("thumbnail", "sources") as? JsonArray)?.firstOrNull() as? JsonObject)
                                ?.get("url")?.string(),
                            seconds = SHORT_UNKNOWN_LENGTH,
                            vertical = true,
                        )?.let(result::add)
                    }
                    (element["videoRenderer"] as? JsonObject)?.let { r ->
                        video(
                            id = r["videoId"]?.string(),
                            title = r.text("title"),
                            channel = r.text("ownerText") ?: r.text("shortBylineText"),
                            thumbnail = r.lastThumbnail("thumbnail"),
                            seconds = r.text("lengthText")?.let(::parseLength) ?: 0,
                            vertical = false,
                        )?.let(result::add)
                    }
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return result
    }

    private fun video(id: String?, title: String?, channel: String?, thumbnail: String?, seconds: Int, vertical: Boolean): Video? {
        if (id.isNullOrBlank()) return null
        return Video(
            uuid = YOUTUBE_UUID_PREFIX + id,
            title = title.orEmpty(),
            channelName = channel.orEmpty(),
            accountHandle = channel?.let { "@$it" }.orEmpty(),
            thumbnailUrl = thumbnail,
            durationSeconds = seconds,
            views = 0,
            likes = 0,
            watchUrl = "https://www.youtube.com/shorts/$id",
            instanceUrl = BASE,
            aspectRatio = if (vertical) 9f / 16f else null,
            platform = PLATFORM_YOUTUBE,
        )
    }

    companion object {
        /** Videos from this "instance" are resolved by the extractor instead of a proxy. */
        const val BASE = "https://m.youtube.com"

        /** Search filter "Under 4 minutes". */
        private const val SHORT_FILTER = "EgIYAQ%253D%253D"

        /** Shorts in search results carry no length; they are at most 60 s (older ones) or a few minutes. */
        private const val SHORT_UNKNOWN_LENGTH = 59

        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    }
}

/**
 * Extracts the JSON object assigned to [variable] in an HTML page
 * (`var ytInitialData = {...};` or `ytInitialPlayerResponse = {...};`), respecting strings and escapes.
 */
fun extractJsonObject(html: String, variable: String): String? {
    var index = html.indexOf("$variable =")
    if (index < 0) index = html.indexOf("$variable=")
    if (index < 0) return null
    val start = html.indexOf('{', index)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return html.substring(start, i + 1)
            }
        }
    }
    return null
}

/** "1:23" → 83, "1:02:03" → 3723. */
fun parseLength(text: String): Int =
    text.trim().split(':').mapNotNull { it.toIntOrNull() }.fold(0) { acc, part -> acc * 60 + part }

private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) current = (current as? JsonObject)?.get(key) ?: return null
    return current
}

/** YouTube text objects are either `{simpleText}` or `{runs:[{text}]}`. */
private fun JsonObject.text(key: String): String? {
    val obj = this[key] as? JsonObject ?: return null
    obj["simpleText"]?.string()?.let { return it }
    return (obj["runs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("text")?.string() }?.joinToString("")
}

private fun JsonObject.lastThumbnail(key: String): String? =
    ((path(key, "thumbnails") as? JsonArray)?.lastOrNull() as? JsonObject)?.get("url")?.string()
