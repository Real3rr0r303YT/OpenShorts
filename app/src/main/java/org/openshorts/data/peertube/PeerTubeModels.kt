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
    val uuid: String,
    val shortUUID: String? = null,
    val name: String = "",
    val duration: Int = 0,
    val views: Long = 0,
    val likes: Long = 0,
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
