package org.openshorts.data.peertube

import kotlinx.serialization.Serializable

/**
 * UI-facing video model, independent of the PeerTube JSON format.
 * Serializable so liked/saved videos can be stored locally and replayed later.
 */
@Serializable
data class Video(
    val uuid: String,
    val title: String,
    val channelName: String,
    val accountHandle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val views: Long,
    val likes: Long,
    val comments: Long = 0,
    val isLive: Boolean = false,
    val watchUrl: String,
    val creatorAvatarUrl: String? = null,
    val creatorUrl: String? = null,
    /** Channel handle like `name@host`, used to follow the creator / open their feed. */
    val channelHandle: String? = null,
    /** Instance the video was loaded from; used to resolve its stream later. */
    val instanceUrl: String = "",
    /** Numeric id, only valid on [instanceUrl]. */
    val id: Long = 0,
    /** Width / height as reported by PeerTube ≥ 6.1; null if unknown. */
    val aspectRatio: Float? = null,
    /** Where the video comes from: [PLATFORM_PEERTUBE] or [PLATFORM_YOUTUBE]. */
    val platform: String = PLATFORM_PEERTUBE,
) {
    /** YouTube videos are played through a Piped/Invidious proxy; likes etc. stay local. */
    val isYouTube: Boolean get() = platform == PLATFORM_YOUTUBE

    /** The YouTube video id for [isYouTube] videos. */
    val youTubeId: String get() = uuid.removePrefix(YOUTUBE_UUID_PREFIX)

    /** Portrait clip (9:16 and similar), the native short-video format. */
    val isVertical: Boolean get() = aspectRatio?.let { it < 0.9f } ?: false
}

const val PLATFORM_PEERTUBE = "peertube"
const val PLATFORM_YOUTUBE = "youtube"
const val YOUTUBE_UUID_PREFIX = "yt:"

/** A (top-level) comment, already converted to plain text. */
data class Comment(
    val id: Long,
    val text: String,
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String?,
    val createdAt: String,
    val replies: Int,
)

data class CommentPage(val total: Int, val comments: List<Comment>)

/** A creator (PeerTube channel) – search result or locally followed. */
@Serializable
data class Creator(
    /** `name@host` */
    val handle: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val url: String? = null,
    val followers: Long = 0,
)
