package dev.typetype.server.services

data class HomeRecommendationTargetPlan(
    val targetSubscription: Int,
    val targetDiscovery: Int,
    val noveltyBudget: Int,
)
