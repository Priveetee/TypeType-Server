package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AudioStreamItem(
    val url: String,
    val format: String,
    val bitrate: Int,
    val quality: String?,
)
