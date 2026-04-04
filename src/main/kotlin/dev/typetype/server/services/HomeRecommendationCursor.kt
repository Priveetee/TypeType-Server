package dev.typetype.server.services

data class HomeRecommendationCursor(
    val subscriptionIndex: Int = 0,
    val discoveryIndex: Int = 0,
    val subscriptionRun: Int = 0,
    val preferDiscovery: Boolean = true,
    val recentChannels: List<String> = emptyList(),
    val recentSemanticKeys: List<String> = emptyList(),
    val creatorMomentum: Map<String, Int> = emptyMap(),
    val creatorCooldownUntilMs: Map<String, Long> = emptyMap(),
    val recentTopicPairs: List<String> = emptyList(),
    val recentUrls: List<String> = emptyList(),
    val personaState: HomeRecommendationPersonaState = HomeRecommendationPersonaState(),
)
