package dev.typetype.server.services

object HomeRecommendationPoolWeights {
    fun forMode(profile: HomeRecommendationProfile, shortsMode: Boolean): Map<HomeRecommendationSourceTag, Double> {
        if (shortsMode) return HomeRecommendationShortsSourceWeights.forProfile(profile)
        val sub = profile.subscriptionEngagement
        val disc = profile.discoveryEngagement
        if (sub == 0.0 && disc == 0.0) return emptyMap()
        val gap = (sub - disc).coerceIn(-8.0, 8.0)
        val subWeight = (1.0 + gap * 0.04).coerceIn(0.65, 1.5)
        val discWeight = (1.0 - gap * 0.03).coerceIn(0.65, 1.5)
        return mapOf(
            HomeRecommendationSourceTag.SUBSCRIPTION to subWeight,
            HomeRecommendationSourceTag.DISCOVERY_TRENDING to discWeight,
            HomeRecommendationSourceTag.DISCOVERY_THEME to discWeight,
            HomeRecommendationSourceTag.DISCOVERY_EXPLORATION to discWeight,
        )
    }
}
