package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class VideoStreamItem(
    val url: String,
    val format: String,
    val resolution: String,
    val bitrate: Int?,
    val codec: String,
    val isVideoOnly: Boolean,
)
