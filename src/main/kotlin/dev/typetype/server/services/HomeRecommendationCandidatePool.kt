package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationCandidatePool(
    val subscriptions: List<VideoItem>,
    val discovery: List<VideoItem>,
)
