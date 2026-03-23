package dev.typetype.server.services

class YoutubeTakeoutPreferenceService(private val recommendationEventService: RecommendationEventService) {
    suspend fun preferredCategories(userId: String): List<String> {
        val hasClickSignals = recommendationEventService.hasClick(userId)
        return if (hasClickSignals) {
            listOf("technology", "gaming", "programming", "news")
        } else {
            listOf("technology", "gaming")
        }
    }
}
