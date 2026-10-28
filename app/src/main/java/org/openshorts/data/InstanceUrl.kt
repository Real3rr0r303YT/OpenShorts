package org.openshorts.data

import java.net.URI

/**
 * Normalises user input like `framatube.org` or `https://tube.example/` into
 * `https://framatube.org`. Returns `null` for invalid or non-HTTPS input.
 */
fun normalizeInstanceUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host?.lowercase() ?: return null
    if ('.' !in host && host != "localhost") return null
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return "https://$host$port$path"
}
