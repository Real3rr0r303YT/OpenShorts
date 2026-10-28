package org.openshorts.data.peertube

/** UI-facing video model, independent of the PeerTube JSON format. */
data class Video(
    val uuid: String,
    val title: String,
    val channelName: String,
    val accountHandle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val views: Long,
    val likes: Long,
    val isLive: Boolean,
    val watchUrl: String,
)
