package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationOfflineEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationOfflineEvaluatorTest {
    @Test
    fun `evaluate returns bounded metrics`() {
        val ranked = listOf(
            video("a", "c1"),
            video("b", "c2"),
            video("c", "c3"),
            video("a", "c1"),
        )
        val metrics = HomeRecommendationOfflineEvaluator.evaluate(
            ranked = ranked,
            clickedUrls = setOf("https://yt.com/v/a", "https://yt.com/v/c"),
        )
        assertTrue(metrics.ndcgAt10 in 0.0..1.0)
        assertEquals(0.75, metrics.diversityAt10)
        assertEquals(0.25, metrics.duplicateRateAt10)
    }

    @Test
    fun `evaluate empty ranked list returns zero metrics`() {
        val metrics = HomeRecommendationOfflineEvaluator.evaluate(emptyList(), setOf("https://yt.com/v/a"))
        assertEquals(0.0, metrics.ndcgAt10)
        assertEquals(0.0, metrics.diversityAt10)
        assertEquals(0.0, metrics.duplicateRateAt10)
    }

    private fun video(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        uploaded = 1L,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
