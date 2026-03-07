package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val duration: Long,
    val viewCount: Long,
    val uploadDate: String,
)
