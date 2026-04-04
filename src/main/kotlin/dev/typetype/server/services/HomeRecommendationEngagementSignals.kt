package dev.typetype.server.services

data class HomeRecommendationEngagementSignals(
    val videoPenalty: Map<String, Double>,
    val implicitBlockedVideos: Set<String>,
)
