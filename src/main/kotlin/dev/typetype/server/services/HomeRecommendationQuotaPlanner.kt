package dev.typetype.server.services

class HomeRecommendationQuotaPlanner(
    private val limit: Int,
    private val subscriptionSize: Int,
    private val discoverySize: Int,
    private val sourceByUrl: Map<String, HomeRecommendationSourceTag>,
) {
    val target = computeTarget()

    fun shouldForceDiscovery(subscriptionCount: Int, discoveryCount: Int, selected: Int): Boolean {
        val remainingSlots = limit - selected
        val remainingNeed = (target.targetDiscovery - discoveryCount).coerceAtLeast(0)
        return remainingNeed >= remainingSlots || subscriptionCount >= target.targetSubscription
    }

    fun shouldPreferDiscovery(subscriptionRun: Int, discoveryCount: Int, preferDiscovery: Boolean): Boolean {
        if (subscriptionRun >= MAX_SUBSCRIPTION_RUN) return true
        if (discoveryCount < target.targetDiscovery) return true
        return preferDiscovery
    }

    private fun computeTarget(): HomeRecommendationTargetPlan {
        val themeWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_THEME }
        val explorationWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_EXPLORATION }
        val trendingWeight = sourceByUrl.values.count { it == HomeRecommendationSourceTag.DISCOVERY_TRENDING }
        val dynamicRatio = when {
            discoverySize == 0 -> 0.0
            subscriptionSize == 0 -> 1.0
            themeWeight + explorationWeight + trendingWeight >= subscriptionSize -> 0.65
            themeWeight + explorationWeight >= subscriptionSize / 2 -> 0.58
            else -> 0.50
        }
        val targetDiscovery = minOf((limit * dynamicRatio).toInt().coerceAtLeast(0), discoverySize)
        val targetSubscription = minOf(limit - targetDiscovery, subscriptionSize)
        return HomeRecommendationTargetPlan(targetSubscription = targetSubscription, targetDiscovery = targetDiscovery)
    }

    companion object {
        const val MAX_SUBSCRIPTION_RUN = 2
    }
}
