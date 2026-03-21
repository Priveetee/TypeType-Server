package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationPage(
    val items: List<VideoItem>,
    val nextCursor: String?,
)
