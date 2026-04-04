package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import dev.typetype.server.services.HomeRecommendationEngagementSplitCalculator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationEngagementSplitCalculatorTest {
    @Test
    fun `calculator separates subscription and discovery engagement`() {
        val events = listOf(
            RecommendationEventItem("1", "watch", "u1", "https://yt.com/c/sub", null, 0.8, null, 1),
            RecommendationEventItem("2", "short_skip", "u2", "https://yt.com/c/disc", null, null, 200, 2),
        )
        val split = HomeRecommendationEngagementSplitCalculator.compute(
            events = events,
            subscriptionChannels = setOf("https://yt.com/c/sub"),
        )
        assertTrue(split.subscriptionEngagement > split.discoveryEngagement)
    }
}
