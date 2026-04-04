package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationPersonalization
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.RecommendationFeedHistoryEntry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationPersonalizationTest {
    @Test
    fun `feed history penalizes repeated shown video`() {
        val now = System.currentTimeMillis()
        val profile = HomeRecommendationProfile(
            seenUrls = emptySet(),
            blockedVideos = emptySet(),
            blockedChannels = emptySet(),
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = emptySet(),
            favoriteUrls = emptySet(),
            watchLaterUrls = emptySet(),
            keywordAffinity = emptySet(),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            feedHistory = mapOf("https://yt.com/v/a" to RecommendationFeedHistoryEntry(showCount = 5, lastShown = now - 1_000L)),
            rejectionTopicPenalty = emptyMap(),
            rejectionChannelPenalty = emptyMap(),
            channelTopicProfile = emptyMap(),
            shortsTopicInterest = emptyMap(),
        )
        val scored = HomeRecommendationPersonalization.applyPenalties(video("a"), score = 2.0, profile = profile)
        assertTrue(scored < 1.0)
    }

    private fun video(id: String): VideoItem = VideoItem(
        id = id,
        title = "title",
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = "uploader",
        uploaderUrl = "https://yt.com/c/uploader",
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 2_000,
        uploadDate = "",
        uploaded = System.currentTimeMillis() - 3_600_000L,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
