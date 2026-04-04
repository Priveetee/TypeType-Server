package dev.typetype.server

import dev.typetype.server.models.RecommendationEventItem
import dev.typetype.server.services.HomeRecommendationShortsSignals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsSignalsTest {
    @Test
    fun `signals penalize repeated instant skips more than late skips`() {
        val urlA = "https://www.youtube.com/shorts/a"
        val urlB = "https://www.youtube.com/shorts/b"
        val now = System.currentTimeMillis()
        val events = listOf(
            event(urlA, 300, now),
            event(urlA, 450, now),
            event(urlA, 600, now),
            event(urlB, 5000, now),
        )
        val penalties = HomeRecommendationShortsSignals.shortSkipPenaltyByUrl(events)
        assertTrue((penalties[urlA] ?: 1.0) < (penalties[urlB] ?: 1.0))
    }

    private fun event(url: String, duration: Long, now: Long): RecommendationEventItem = RecommendationEventItem(
        id = "id-$url-$duration",
        eventType = "short_skip",
        videoUrl = url,
        uploaderUrl = null,
        title = "short",
        watchRatio = null,
        watchDurationMs = duration,
        contextKey = "0:we:afternoon:quick:unknown",
        occurredAt = now,
    )
}
