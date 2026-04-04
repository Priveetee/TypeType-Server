package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationShortsQueryFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsQueryFactoryTest {
    @Test
    fun `query factory filters generic keywords and keeps profile terms`() {
        val profile = HomeRecommendationProfile(
            seenUrls = emptySet(),
            blockedVideos = emptySet(),
            blockedChannels = emptySet(),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = emptySet(),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = setOf("linux", "shorts", "random", "kernel"),
            themeTokens = emptySet(),
            themeQueries = listOf("linux desktop"),
        )
        val queries = HomeRecommendationShortsQueryFactory.fromProfile(profile, limit = 6)
        assertTrue(queries.any { it.contains("linux") || it.contains("kernel") })
        assertTrue(queries.none { it == "random shorts" })
    }
}
