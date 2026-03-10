package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val uploaderAvatarUrl: String,
    val duration: Long,
    val viewCount: Long,
    val uploadDate: String,
    val uploaded: Long = -1L,
    val streamType: String,
    val isShortFormContent: Boolean,
    val uploaderVerified: Boolean,
    val shortDescription: String?,
)
