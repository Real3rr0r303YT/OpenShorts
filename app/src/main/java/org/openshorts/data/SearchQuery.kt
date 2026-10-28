package org.openshorts.data

/** Default "Tech" feed: videos tagged with any of these (PeerTube `tagsOneOf`). */
val TECH_TAGS = listOf("cybersecurity", "hacking", "linux", "infosec", "programming", "tech")

/** What the search bar understands. */
sealed interface SearchQuery {
    val text: String

    /** Free text: matched against titles, descriptions and tags. */
    data class Text(override val text: String) : SearchQuery

    /** `#linux` → videos with that tag. */
    data class Tag(val tag: String) : SearchQuery {
        override val text: String get() = "#$tag"
    }

    /** `@name` or `@name@host` → videos of that channel. */
    data class Channel(val handle: String) : SearchQuery {
        override val text: String get() = "@$handle"
    }
}

/** Parses search bar input; returns null for blank input. */
fun parseSearchQuery(input: String): SearchQuery? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> null
        trimmed.startsWith("#") -> trimmed.drop(1).trim().lowercase()
            .takeIf { it.isNotEmpty() && ' ' !in it }
            ?.let { SearchQuery.Tag(it) }
            ?: SearchQuery.Text(trimmed.drop(1).trim())
        trimmed.startsWith("@") -> trimmed.drop(1).trim()
            .takeIf { it.isNotEmpty() && ' ' !in it }
            ?.let { SearchQuery.Channel(it) }
            ?: SearchQuery.Text(trimmed.drop(1).trim())
        else -> SearchQuery.Text(trimmed)
    }
}
