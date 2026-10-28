package org.openshorts.web

import java.net.URLEncoder

/** Start page of the in-app browser. */
const val BROWSER_HOME = "https://www.tiktok.com/"

/** YouTube's own Shorts web feed (vertical swipe player), used as a fallback for the native feed. */
const val YOUTUBE_SHORTS_WEB = "https://www.youtube.com/shorts"

private val DOMAIN_LIKE = Regex("""^[a-z0-9-]+(\.[a-z0-9-]+)+(:\d+)?(/\S*)?$""", RegexOption.IGNORE_CASE)

/**
 * Turns address bar input into a URL:
 * - `https://…` stays, `http://…` is upgraded to HTTPS (the app allows no cleartext traffic),
 * - `tiktok.com/@name` or `example.org` → `https://…`,
 * - `@name` → the TikTok profile,
 * - anything else → a TikTok search for the words.
 * Returns null for blank input.
 */
fun browserTarget(input: String): String? {
    val text = input.trim()
    return when {
        text.isEmpty() -> null
        text.startsWith("https://", ignoreCase = true) -> text
        text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring("http://".length)
        text.startsWith("@") && text.length > 1 && ' ' !in text -> "https://www.tiktok.com/$text"
        ' ' !in text && DOMAIN_LIKE.matches(text) -> "https://$text"
        else -> "https://www.tiktok.com/search?q=" + URLEncoder.encode(text, "UTF-8")
    }
}

/** Short form of a URL for the address bar when it is not focused ("tiktok.com/@name"). */
fun displayUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removePrefix("m.").trimEnd('/')
