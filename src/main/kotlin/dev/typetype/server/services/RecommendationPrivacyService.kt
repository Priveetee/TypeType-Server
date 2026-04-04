package dev.typetype.server.services

class RecommendationPrivacyService(private val settingsService: SettingsService) {
    suspend fun isPersonalizationEnabled(userId: String): Boolean =
        settingsService.get(userId).recommendationPersonalizationEnabled
}
