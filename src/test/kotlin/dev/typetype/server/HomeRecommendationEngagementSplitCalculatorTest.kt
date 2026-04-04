package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import dev.typetype.server.services.HomeRecommendationEngagementSplitCalculator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationEngagementSplitCalculatorTest {
    @Test
    fun `calculator separates subscription and discovery engagement`() {
        val events = listOf(
            RecommendationEventItem(
                id = "1",
                eventType = "watch",
                videoUrl = "u1",
                uploaderUrl = "https://yt.com/c/sub",
                title = null,
                watchRatio = 0.8,
                watchDurationMs = null,
                contextKey = null,
                occurredAt = 1,
            ),
            RecommendationEventItem(
                id = "2",
                eventType = "short_skip",
                videoUrl = "u2",
                uploaderUrl = "https://yt.com/c/disc",
                title = null,
                watchRatio = null,
                watchDurationMs = 200,
                contextKey = null,
                occurredAt = 2,
            ),
        )
        val split = HomeRecommendationEngagementSplitCalculator.compute(
            events = events,
            subscriptionChannels = setOf("https://yt.com/c/sub"),
        )
        assertTrue(split.subscriptionEngagement > split.discoveryEngagement)
    }
}
