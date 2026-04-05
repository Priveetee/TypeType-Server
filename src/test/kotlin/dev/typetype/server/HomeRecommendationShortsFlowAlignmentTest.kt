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

class HomeRecommendationShortsFlowAlignmentTest {
    @Test
    fun `shorts mix keeps discovery floor with subscription heavy pool`() {
        val subs = (1..30).map { index -> short("s$index", "sub$index") }
        val discovery = (1..12).map { index -> short("d$index", "disc$index") }
        val pool = HomeRecommendationPool(subscriptions = subs, discovery = discovery)
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = HomeRecommendationCursor(),
            limit = 10,
            context = context,
            mode = HomeRecommendationPoolMode.SHORTS,
        )
        assertTrue(page.discoveryCount >= 6)
    }

    private fun short(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://youtube.com/shorts/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 30,
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
