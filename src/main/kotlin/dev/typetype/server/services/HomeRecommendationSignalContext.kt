package dev.typetype.server.services

data class HomeRecommendationSignalContext(
    val userSubscriptions: List<String>,
    val historyItems: List<String>,
)
