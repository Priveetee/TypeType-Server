package dev.typetype.server

import dev.typetype.server.services.HomeRecommendationShortsSeenMemory
import dev.typetype.server.services.RecommendationFeedHistoryEntry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsSeenMemoryTest {
    @Test
    fun `seen memory applies stronger penalty for repeated recent shorts`() {
        val now = System.currentTimeMillis()
        val light = HomeRecommendationShortsSeenMemory.penalty(
            RecommendationFeedHistoryEntry(showCount = 1, lastShown = now - 30L * 60L * 1000L),
            now = now,
        )
        val heavy = HomeRecommendationShortsSeenMemory.penalty(
            RecommendationFeedHistoryEntry(showCount = 4, lastShown = now - 30L * 60L * 1000L),
            now = now,
        )
        assertTrue(heavy < light)
    }
}
