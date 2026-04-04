package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import dev.typetype.server.services.HomeRecommendationEventAnalyzer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationEventAnalyzerTest {
    @Test
    fun `marks repeated unclicked impressions as implicitly blocked`() {
        val now = System.currentTimeMillis()
        val events = (1..5).map { index ->
            RecommendationEventItem(
                id = index.toString(),
                eventType = "impression",
                videoUrl = "https://yt.com/v/a",
                uploaderUrl = "https://yt.com/c/a",
                title = null,
                watchRatio = null,
                occurredAt = now - index * 1_000L,
            )
        }
        val signals = HomeRecommendationEventAnalyzer.buildSignals(events)
        assertTrue("https://yt.com/v/a" in signals.implicitBlockedVideos)
        assertEquals(0.10, signals.videoPenalty["https://yt.com/v/a"])
    }

    @Test
    fun `keeps medium penalty when impressions are repeated without clicks`() {
        val now = System.currentTimeMillis()
        val events = (1..3).map { index ->
            RecommendationEventItem(
                id = index.toString(),
                eventType = "impression",
                videoUrl = "https://yt.com/v/b",
                uploaderUrl = "https://yt.com/c/b",
                title = null,
                watchRatio = null,
                occurredAt = now - index * 1_000L,
            )
        }
        val signals = HomeRecommendationEventAnalyzer.buildSignals(events)
        assertTrue("https://yt.com/v/b" !in signals.implicitBlockedVideos)
        assertEquals(0.30, signals.videoPenalty["https://yt.com/v/b"])
    }
}
