package dev.typetype.server.services

data class HomeRecommendationProfile(
    val seenUrls: Set<String>,
    val blockedVideos: Set<String>,
    val blockedChannels: Set<String>,
    val feedbackBlockedVideos: Set<String>,
    val feedbackBlockedChannels: Set<String>,
    val subscriptionChannels: Set<String>,
    val favoriteUrls: Set<String>,
    val watchLaterUrls: Set<String>,
    val keywordAffinity: Set<String>,
    val themeTokens: Set<String>,
    val themeQueries: List<String>,
    val channelInterest: Map<String, Double> = emptyMap(),
    val topicInterest: Map<String, Double> = emptyMap(),
    val eventPenaltyByVideo: Map<String, Double> = emptyMap(),
    val implicitBlockedVideos: Set<String> = emptySet(),
)
