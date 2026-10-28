package org.openshorts.data.youtube

import kotlinx.serialization.Serializable

// Piped API (https://docs.piped.video/docs/api-documentation/)

@Serializable
data class PipedSearchResponse(
    val items: List<PipedItem> = emptyList(),
    val nextpage: String? = null,
)

@Serializable
data class PipedItem(
    val url: String = "",
    val type: String = "",
    val title: String = "",
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val duration: Long = 0,
    val views: Long = 0,
    val isShort: Boolean = false,
)

@Serializable
data class PipedStreams(
    val hls: String? = null,
    val videoStreams: List<PipedStream> = emptyList(),
    val likes: Long = 0,
)

@Serializable
data class PipedStream(
    val url: String = "",
    val mimeType: String? = null,
    val quality: String? = null,
    val videoOnly: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class PipedComments(
    val comments: List<PipedComment> = emptyList(),
)

@Serializable
data class PipedComment(
    val author: String = "",
    val thumbnail: String? = null,
    val commentId: String = "",
    val commentText: String = "",
    val commentedTime: String = "",
    val replyCount: Int = 0,
)

// Invidious API (https://docs.invidious.io/api/)

@Serializable
data class InvidiousVideoItem(
    val type: String = "",
    val title: String = "",
    val videoId: String = "",
    val author: String = "",
    val authorId: String = "",
    val lengthSeconds: Long = 0,
    val viewCount: Long = 0,
    val videoThumbnails: List<InvidiousThumbnail> = emptyList(),
)

@Serializable
data class InvidiousThumbnail(
    val quality: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class InvidiousVideo(
    val formatStreams: List<InvidiousFormat> = emptyList(),
    val hlsUrl: String? = null,
)

@Serializable
data class InvidiousFormat(
    val url: String = "",
    val type: String = "",
    val resolution: String? = null,
)

@Serializable
data class InvidiousComments(
    val comments: List<InvidiousComment> = emptyList(),
)

@Serializable
data class InvidiousComment(
    val author: String = "",
    val authorThumbnails: List<InvidiousThumbnail> = emptyList(),
    val content: String = "",
    val publishedText: String = "",
    val commentId: String = "",
)
