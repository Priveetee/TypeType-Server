package dev.typetype.server.services

object HomeRecommendationShortsFallback {
    fun apply(pool: HomeRecommendationCandidatePool): HomeRecommendationCandidatePool {
        if (pool.discovery.isNotEmpty() || pool.subscriptions.size >= 6) return pool
        val recoveryDiscovery = pool.subscriptions
            .asSequence()
            .drop(2)
            .take(24)
            .map { it.copy(source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION) }
            .toList()
        if (recoveryDiscovery.isEmpty()) return pool
        return HomeRecommendationCandidatePool(
            subscriptions = pool.subscriptions,
            discovery = (pool.discovery + recoveryDiscovery).distinctBy { it.video.url },
        )
    }
}
