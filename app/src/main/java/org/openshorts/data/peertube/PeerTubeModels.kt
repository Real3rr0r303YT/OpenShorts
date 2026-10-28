package org.openshorts.data.peertube

import kotlinx.serialization.Serializable

// Subset of the PeerTube REST API (https://docs.joinpeertube.org/api-rest-reference.html).

@Serializable
data class VideoListResponse(
    val total: Int = 0,
    val data: List<PeerTubeVideo> = emptyList(),
)

@Serializable
data class PeerTubeVideo(
    val id: Long = 0,
    val uuid: String,
    val shortUUID: String? = null,
    val name: String = "",
    val duration: Int = 0,
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val isLive: Boolean = false,
    val thumbnailPath: String? = null,
    val previewPath: String? = null,
    val account: PeerTubeActor? = null,
    val channel: PeerTubeActor? = null,
)

@Serializable
data class PeerTubeActor(
    val name: String = "",
    val displayName: String = "",
    val host: String = "",
    val url: String? = null,
    val followersCount: Long = 0,
    /** PeerTube < 6 */
    val avatar: ActorImage? = null,
    /** PeerTube ≥ 4: several sizes */
    val avatars: List<ActorImage> = emptyList(),
)

@Serializable
data class ActorImage(
    val path: String? = null,
    val width: Int = 0,
)

@Serializable
data class VideoDetails(
    val uuid: String,
    val files: List<VideoFile> = emptyList(),
    val streamingPlaylists: List<StreamingPlaylist> = emptyList(),
)

@Serializable
data class StreamingPlaylist(
    val playlistUrl: String? = null,
    val files: List<VideoFile> = emptyList(),
)

@Serializable
data class VideoFile(
    val fileUrl: String? = null,
    val resolution: Resolution? = null,
)

@Serializable
data class Resolution(
    val id: Int = 0,
    val label: String = "",
)

@Serializable
data class CommentThreadsResponse(
    val total: Int = 0,
    val data: List<PeerTubeComment> = emptyList(),
)

@Serializable
data class PeerTubeComment(
    val id: Long = 0,
    val text: String = "",
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val totalReplies: Int = 0,
    val account: PeerTubeActor? = null,
)

@Serializable
data class ChannelSearchResponse(
    val total: Int = 0,
    val data: List<PeerTubeActor> = emptyList(),
)
