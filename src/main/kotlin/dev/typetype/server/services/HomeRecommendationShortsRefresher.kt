package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool

object HomeRecommendationShortsRefresher {
    fun refresh(pool: HomeRecommendationPool, page: HomeRecommendationPage): HomeRecommendationPool {
        if (page.discoveryCount > 0) return pool
        val recovered = pool.subscriptions
            .drop(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_DROP)
            .take(HomeRecommendationShortsSources.DISCOVERY_RECOVERY_TAKE)
        if (recovered.isEmpty()) return pool
        val mergedDiscovery = (pool.discovery + recovered).distinctBy { it.url }
        return pool.copy(discovery = mergedDiscovery)
    }
}
