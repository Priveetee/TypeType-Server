package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamResponse(
    val id: String,
    val title: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val uploaderAvatarUrl: String,
    val thumbnailUrl: String,
    val description: String,
    val duration: Long,
    val viewCount: Long,
    val likeCount: Long,
    val dislikeCount: Long,
    val uploadDate: String,
    val hlsUrl: String,
    val dashMpdUrl: String,
    val videoStreams: List<VideoStreamItem>,
    val audioStreams: List<AudioStreamItem>,
    val videoOnlyStreams: List<VideoStreamItem>,
    val sponsorBlockSegments: List<SponsorBlockSegmentItem>,
    val relatedStreams: List<VideoItem>,
)
