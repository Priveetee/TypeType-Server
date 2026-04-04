package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationPoolBuilder
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo
import dev.typetype.server.services.RecommendationFeedHistoryEntry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationPoolBuilderPersonalizationOffTest {
    @Test
    fun `pool builder ignores personalization penalties when kill switch disabled`() {
        val video = video("a", "channel")
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
            feedHistory = mapOf(video.url to RecommendationFeedHistoryEntry(showCount = 8, lastShown = System.currentTimeMillis())),
            rejectionTopicPenalty = mapOf("title" to 0.2),
            rejectionChannelPenalty = mapOf(video.uploaderUrl to 0.2),
            personalizationEnabled = false,
        )
        val pool = HomeRecommendationPoolBuilder().build(
            profile = profile,
            subscriptionCandidates = listOf(HomeRecommendationTaggedVideo(video, HomeRecommendationSourceTag.SUBSCRIPTION)),
            discoveryCandidates = emptyList(),
        )
        assertTrue(pool.subscriptions.isNotEmpty())
    }

    private fun video(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = "title $id",
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
