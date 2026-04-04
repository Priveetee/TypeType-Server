package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import dev.typetype.server.services.HomeRecommendationEventAnalyzer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationEventAnalyzerRejectionTest {
    @Test
    fun `analyzer emits topic and channel rejection penalties from repeated short skips`() {
        val now = System.currentTimeMillis()
        val events = (1..6).map { index ->
            RecommendationEventItem(
                id = index.toString(),
                eventType = "short_skip",
                videoUrl = "https://yt.com/v/$index",
                uploaderUrl = "https://yt.com/c/reject",
                title = "music remix $index",
                watchRatio = null,
                watchDurationMs = 200L,
                occurredAt = now - (index * 1_000L),
            )
        }
        val signals = HomeRecommendationEventAnalyzer.buildSignals(events)
        assertTrue((signals.rejectionChannelPenalty["https://yt.com/c/reject"] ?: 1.0) < 1.0)
        assertTrue((signals.rejectionTopicPenalty["music"] ?: 1.0) < 1.0)
    }
}
