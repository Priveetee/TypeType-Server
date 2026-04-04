package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationMixer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationSubscriptionQuotaSourceTest {
    @Test
    fun `mix counts discovery from subscribed channels as subscription quota`() {
        val subscribedChannel = "https://yt.com/c/subscribed"
        val pool = HomeRecommendationPool(
            subscriptions = listOf(video("s1", "subscribed").copy(uploaderUrl = subscribedChannel), video("s2", "alt")),
            discovery = listOf(
                video("d1", "subscribed").copy(uploaderUrl = subscribedChannel),
                video("d2", "disc"),
                video("d3", "disc2"),
            ),
            subscriptionChannels = setOf(subscribedChannel),
        )
        val page = HomeRecommendationMixer.mix(pool = pool, cursor = HomeRecommendationCursor(), limit = 4)
        assertTrue(page.subscriptionCount >= 2)
    }

    private fun video(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 1,
        viewCount = 0,
        uploadDate = "",
        uploaded = 0,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
