package dev.typetype.server.services

import dev.typetype.server.models.RecommendationOnboardingTopicGroup

object RecommendationOnboardingCatalog {
    const val MIN_TOPICS = 3

    fun groups(): List<RecommendationOnboardingTopicGroup> = listOf(
        RecommendationOnboardingTopicGroup(
            id = "gaming",
            label = "Gaming",
            topics = listOf("GTA", "Minecraft", "Call of Duty", "Speedrun", "Esports"),
        ),
        RecommendationOnboardingTopicGroup(
            id = "tech",
            label = "Technology",
            topics = listOf("Linux", "Programming", "AI", "Cybersecurity", "Hardware"),
        ),
        RecommendationOnboardingTopicGroup(
            id = "news",
            label = "News",
            topics = listOf("World News", "Politics", "Economy", "Geopolitics", "Analysis"),
        ),
        RecommendationOnboardingTopicGroup(
            id = "entertainment",
            label = "Entertainment",
            topics = listOf("Comedy", "Movies", "Series", "True Crime", "Documentary"),
        ),
        RecommendationOnboardingTopicGroup(
            id = "lifestyle",
            label = "Lifestyle",
            topics = listOf("Food", "Travel", "Fitness", "Cars", "Fashion"),
        ),
    )
}
