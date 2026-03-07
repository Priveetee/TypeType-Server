package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamResponse(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val thumbnailUrl: String,
    val description: String,
    val duration: Long,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String,
    val hlsUrl: String,
    val dashMpdUrl: String,
    val videoStreams: List<VideoStreamItem>,
    val audioStreams: List<AudioStreamItem>,
    val videoOnlyStreams: List<VideoStreamItem>,
)
