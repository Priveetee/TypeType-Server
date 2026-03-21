package dev.typetype.server.services

class HomeRecommendationQuotaPlanner(
    private val limit: Int,
    private val subscriptionSize: Int,
    private val discoverySize: Int,
) {
    val minDiscovery: Int = minOf(limit / 2, discoverySize)
    val maxSubscription: Int = minOf(limit / 2, subscriptionSize)
    val idealDiscovery: Int = minOf((limit * 6) / 10, discoverySize)

    fun shouldForceDiscovery(subscriptionCount: Int, discoveryCount: Int, selected: Int): Boolean {
        val remainingSlots = limit - selected
        val remainingNeed = (minDiscovery - discoveryCount).coerceAtLeast(0)
        return remainingNeed >= remainingSlots || subscriptionCount >= maxSubscription
    }

    fun shouldPreferDiscovery(subscriptionRun: Int, discoveryCount: Int, preferDiscovery: Boolean): Boolean {
        if (subscriptionRun >= MAX_SUBSCRIPTION_RUN) return true
        if (discoveryCount < idealDiscovery) return true
        return preferDiscovery
    }

    companion object {
        const val MAX_SUBSCRIPTION_RUN = 2
    }
}
