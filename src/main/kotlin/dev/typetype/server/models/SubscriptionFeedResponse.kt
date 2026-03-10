package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionFeedResponse(
    val videos: List<VideoItem>,
    val nextpage: String?,
)
