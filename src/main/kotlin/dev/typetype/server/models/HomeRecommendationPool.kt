package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationPool(
    val subscriptions: List<VideoItem>,
    val discovery: List<VideoItem>,
)
