package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationMixer
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationSubscriptionQuotaSourceTest {
    @Test
    fun `mix counts by explicit source tag not subscription channel`() {
        val subscribedChannel = "https://yt.com/c/subscribed"
        val pool = HomeRecommendationPool(
            subscriptions = listOf(video("s1", "subscribed").copy(uploaderUrl = subscribedChannel), video("s2", "alt")),
            discovery = listOf(
                video("d1", "subscribed").copy(uploaderUrl = subscribedChannel),
                video("d2", "disc"),
                video("d3", "disc2"),
            ),
            subscriptionChannels = setOf(subscribedChannel),
            sourceByUrl = mapOf(
                "https://yt.com/v/s1" to dev.typetype.server.services.HomeRecommendationSourceTag.SUBSCRIPTION,
                "https://yt.com/v/s2" to dev.typetype.server.services.HomeRecommendationSourceTag.SUBSCRIPTION,
                "https://yt.com/v/d1" to dev.typetype.server.services.HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
                "https://yt.com/v/d2" to dev.typetype.server.services.HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
                "https://yt.com/v/d3" to dev.typetype.server.services.HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
            ),
        )
        val page = HomeRecommendationMixer.mix(pool = pool, cursor = HomeRecommendationCursor(), limit = 4, context = context)
        assertTrue(page.discoveryCount >= 2)
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

    private val context = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    )
}
