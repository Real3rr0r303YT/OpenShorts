package org.openshorts.data.peertube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Authenticated part of the PeerTube REST API.

@Serializable
data class OAuthClient(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class OAuthToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

@Serializable
data class MeResponse(
    val id: Long = 0,
    val username: String = "",
    val account: MeAccount? = null,
    val videoChannels: List<MeChannel> = emptyList(),
)

@Serializable
data class MeAccount(
    val id: Long = 0,
    val name: String = "",
    val displayName: String = "",
    val description: String? = null,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val avatar: ActorImage? = null,
    val avatars: List<ActorImage> = emptyList(),
)

@Serializable
data class MeChannel(
    val id: Long,
    val name: String = "",
    val displayName: String = "",
)

@Serializable
data class SubscriptionsResponse(
    val total: Int = 0,
    val data: List<PeerTubeActor> = emptyList(),
)

@Serializable
data class RatingsResponse(
    val total: Int = 0,
    val data: List<RatingEntry> = emptyList(),
)

@Serializable
data class RatingEntry(
    val video: PeerTubeVideo? = null,
    val rating: String = "",
)

@Serializable
data class PlaylistsResponse(
    val total: Int = 0,
    val data: List<PlaylistSummary> = emptyList(),
)

@Serializable
data class PlaylistSummary(val id: Long)

@Serializable
data class PlaylistElementsResponse(
    val total: Int = 0,
    val data: List<PlaylistElement> = emptyList(),
)

@Serializable
data class PlaylistElement(
    val id: Long,
    val video: PeerTubeVideo? = null,
)

@Serializable
data class PlaylistElementRef(
    val playlistElementId: Long,
    val playlistId: Long,
)

@Serializable
data class CreatedComment(val comment: PeerTubeComment? = null)

@Serializable
data class UploadedVideo(val video: UploadedVideoRef? = null)

@Serializable
data class UploadedVideoRef(
    val id: Long = 0,
    val uuid: String = "",
    val shortUUID: String? = null,
)

/** Body for starting a resumable upload. */
@Serializable
data class UploadMetadata(
    val name: String,
    val channelId: Long,
    val filename: String,
    val privacy: Int,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val waitTranscoding: Boolean = true,
)
