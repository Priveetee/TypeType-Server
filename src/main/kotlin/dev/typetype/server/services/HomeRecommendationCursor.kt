package dev.typetype.server.services

data class HomeRecommendationCursor(
    val subscriptionIndex: Int = 0,
    val discoveryIndex: Int = 0,
    val subscriptionRun: Int = 0,
    val preferDiscovery: Boolean = true,
    val recentChannels: List<String> = emptyList(),
)
