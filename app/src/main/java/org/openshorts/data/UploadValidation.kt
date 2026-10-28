package org.openshorts.data

/** PeerTube's limits for video metadata. */
data class UploadValidation(
    val titleValid: Boolean,
    val tagsValid: Boolean,
    val hasVideo: Boolean,
    val hasChannel: Boolean,
) {
    val isValid: Boolean get() = titleValid && tagsValid && hasVideo && hasChannel

    companion object {
        const val MIN_TITLE = 3
        const val MAX_TITLE = 120
        const val MAX_DESCRIPTION = 10_000
        const val MAX_TAGS = 5
        const val MIN_TAG = 2
        const val MAX_TAG = 30

        fun check(title: String, tags: List<String>, hasVideo: Boolean, hasChannel: Boolean) = UploadValidation(
            titleValid = title.trim().length in MIN_TITLE..MAX_TITLE,
            tagsValid = tags.size <= MAX_TAGS && tags.all { it.length in MIN_TAG..MAX_TAG },
            hasVideo = hasVideo,
            hasChannel = hasChannel,
        )
    }
}

/** "#CyberSecurity " → "cybersecurity"; null if it is not a valid PeerTube tag. */
fun normalizeTag(raw: String): String? =
    raw.trim().removePrefix("#").trim().lowercase()
        .takeIf { it.length in UploadValidation.MIN_TAG..UploadValidation.MAX_TAG && it.none(Char::isWhitespace) }
