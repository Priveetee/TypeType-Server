package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationJitter
import dev.typetype.server.services.HomeRecommendationScoredVideo
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.RecommendationFeedHistoryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeRecommendationJitterTest {
    @Test
    fun `apply keeps candidate count and deterministic order size`() {
        val scored = listOf(
            scored("a", 1.0),
            scored("b", 0.9),
            scored("c", 0.8),
        )
        val history = mapOf(
            "https://yt.com/v/a" to RecommendationFeedHistoryEntry(4, System.currentTimeMillis()),
            "https://yt.com/v/b" to RecommendationFeedHistoryEntry(2, System.currentTimeMillis()),
        )
        val out = HomeRecommendationJitter.apply(scored, history)
        assertEquals(3, out.size)
    }

    private fun scored(id: String, score: Double): HomeRecommendationScoredVideo = HomeRecommendationScoredVideo(
        video = VideoItem(
            id = id,
            title = id,
            url = "https://yt.com/v/$id",
            thumbnailUrl = "",
            uploaderName = "u$id",
            uploaderUrl = "https://yt.com/c/u$id",
            uploaderAvatarUrl = "",
            duration = 10,
            viewCount = 0,
            uploadDate = "",
            uploaded = 0,
            streamType = "video_stream",
            isShortFormContent = false,
            uploaderVerified = false,
            shortDescription = null,
        ),
        score = score,
        source = HomeRecommendationSourceTag.DISCOVERY_TRENDING,
    )
}
