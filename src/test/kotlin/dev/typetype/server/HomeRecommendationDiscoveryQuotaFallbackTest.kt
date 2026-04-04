package dev.typetype.server

import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationPoolBuilder
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationProfile
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.HomeRecommendationSourceTag
import dev.typetype.server.services.HomeRecommendationTaggedVideo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationDiscoveryQuotaFallbackTest {
    @Test
    fun `pool builder keeps low-theme discovery candidates for quota mixing`() {
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
            themeTokens = setOf("linux", "kernel", "arch"),
            themeQueries = listOf("linux kernel"),
            channelInterest = emptyMap(),
            topicInterest = emptyMap(),
        )
        val discovery = listOf(
            HomeRecommendationTaggedVideo(
                video = video("broad1", "news", title = "Football match recap"),
                source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
            ),
        )
        val pool = HomeRecommendationPoolBuilder().build(profile, emptyList(), discovery, context)
        assertEquals(1, pool.discovery.size)
        assertTrue(pool.discovery.first().url.endsWith("/broad1"))
    }

    private fun video(id: String, channel: String, title: String): VideoItem = VideoItem(
        id = id,
        title = title,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 1,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    private val context = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    )
}
