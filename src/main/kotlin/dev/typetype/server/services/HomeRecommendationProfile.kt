package dev.typetype.server.services

data class HomeRecommendationProfile(
    val seenUrls: Set<String>,
    val blockedVideos: Set<String>,
    val blockedChannels: Set<String>,
    val subscriptionChannels: Set<String>,
    val favoriteUrls: Set<String>,
    val watchLaterUrls: Set<String>,
    val channelAffinity: Map<String, Double>,
    val keywordAffinity: Set<String>,
)
