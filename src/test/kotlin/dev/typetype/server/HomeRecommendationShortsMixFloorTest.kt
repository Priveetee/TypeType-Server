package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationMixer
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsMixFloorTest {
    @Test
    fun `shorts mix keeps discovery majority for auto intent`() {
        val subs = (1..12).map { index -> video("s$index", "sub$index") }
        val discovery = (1..12).map { index -> video("d$index", "disc$index") }
        val pool = HomeRecommendationPool(subscriptions = subs, discovery = discovery)
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(),
            limit = 10,
            context = context,
            mode = HomeRecommendationPoolMode.SHORTS,
        )
        assertTrue(page.discoveryCount >= 6)
        assertTrue(page.targetDiscoveryRatio >= 0.60)
        assertTrue(page.discoveryFloorRatio >= 0.60)
    }

    private fun video(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 20,
        viewCount = 0,
        uploadDate = "",
        uploaded = 0,
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )

    private val context = HomeRecommendationSessionContext(
        intent = HomeRecommendationSessionIntent.AUTO,
        deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
    )
}
