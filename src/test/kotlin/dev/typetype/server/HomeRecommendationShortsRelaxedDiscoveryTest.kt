package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationMixer
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.HomeRecommendationSemanticKey
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsRelaxedDiscoveryTest {
    @Test
    fun `shorts keeps discovery picks when strict novelty filters block`() {
        val subs = (1..20).map { index -> short("s$index", "sub$index", "subs track $index") }
        val discovery = (1..20).map { index -> short("d$index", "disc$index", "alpha beta gamma") }
        val blockedSemantic = HomeRecommendationSemanticKey.fromTitle("alpha beta gamma")
        val cursor = HomeRecommendationCursor(recentSemanticKeys = listOf(blockedSemantic))
        val page = HomeRecommendationMixer.mix(
            pool = HomeRecommendationPool(subscriptions = subs, discovery = discovery),
            cursor = cursor,
            limit = 10,
            context = context,
            mode = HomeRecommendationPoolMode.SHORTS,
        )
        assertTrue(page.discoveryCount >= 6)
    }

    private fun short(id: String, channel: String, title: String): VideoItem = VideoItem(
        id = id,
        title = title,
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
