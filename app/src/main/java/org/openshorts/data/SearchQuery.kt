package org.openshorts.data

/** Default "Tech" feed: videos tagged with any of these (PeerTube `tagsOneOf`). */
val TECH_TAGS = listOf("cybersecurity", "hacking", "linux", "infosec", "programming", "tech")

/** Trending categories shown as badges in the search and matched by plain-word searches. */
enum class TrendCategory(val label: String, val emoji: String, val tags: List<String>, val keywords: Set<String>) {
    EDITS("Edits", "✂️", listOf("edit", "edits", "amv", "fanedit", "videoedit"), setOf("edit", "edits", "fanedit", "amv")),
    CYBERSECURITY(
        "Cybersecurity", "🛡️",
        listOf("cybersecurity", "infosec", "hacking", "security", "ctf"),
        setOf("cybersecurity", "cyber security", "infosec", "hacking", "security"),
    ),
    GAMING("Gaming", "🎮", listOf("gaming", "games", "gameplay", "videogames", "speedrun"), setOf("gaming", "games", "game", "gameplay")),
    TECH("Tech", "💻", listOf("tech", "linux", "programming", "opensource", "technology"), setOf("tech", "technology", "linux", "programming")),
    ANIME("Anime", "🌸", listOf("anime", "amv", "manga", "animation", "otaku"), setOf("anime", "manga")),
    SHORTS("Shorts", "⚡", listOf("shorts", "short", "vertical", "reels", "tiktok"), setOf("shorts", "short", "reels")),
    ;

    companion object {
        fun match(text: String): TrendCategory? {
            val normalized = text.trim().lowercase()
            return entries.firstOrNull { normalized in it.keywords }
        }
    }
}

/** What the search bar understands. */
sealed interface SearchQuery {
    val text: String

    /** Free text: matched against titles, descriptions and tags. */
    data class Text(override val text: String) : SearchQuery

    /** `#linux` → videos with that tag. */
    data class Tag(val tag: String) : SearchQuery {
        override val text: String get() = "#$tag"
    }

    /** A trend category (e.g. "edits", "gaming") → short clips with any of its tags. */
    data class Category(val category: TrendCategory) : SearchQuery {
        override val text: String get() = "${category.emoji} ${category.label}"
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
        else -> TrendCategory.match(trimmed)?.let { SearchQuery.Category(it) } ?: SearchQuery.Text(trimmed)
    }
}
