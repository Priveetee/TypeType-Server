package dev.typetype.server

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationPage
import dev.typetype.server.services.HomeRecommendationShortsRefresher
import dev.typetype.server.services.HomeRecommendationSourceTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeRecommendationShortsRefresherSourceTagTest {
    @Test
    fun `refresh tags recovered subscriptions as discovery exploration`() {
        val subscriptions = (1..8).map { index -> short("s$index") }
        val sourceByUrl = subscriptions.associate { it.url to HomeRecommendationSourceTag.SUBSCRIPTION }
        val pool = HomeRecommendationPool(
            subscriptions = subscriptions,
            discovery = emptyList(),
            sourceByUrl = sourceByUrl,
        )
        val page = HomeRecommendationPage(
            items = subscriptions.take(8),
            nextCursor = null,
            subscriptionCount = 8,
            discoveryCount = 0,
            targetDiscoveryRatio = 0.6,
            discoveryFloorRatio = 0.65,
            sourceByUrl = emptyMap(),
        )
        val refreshed = HomeRecommendationShortsRefresher.refresh(
            pool = pool,
            page = page,
            cursor = HomeRecommendationCursor(discoveryIndex = 40),
        )
        assertTrue(refreshed.pool.discovery.isNotEmpty())
        refreshed.pool.discovery.forEach { video ->
            assertEquals(HomeRecommendationSourceTag.DISCOVERY_EXPLORATION, refreshed.pool.sourceByUrl[video.url])
        }
    }

    private fun short(id: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://youtube.com/shorts/$id",
        thumbnailUrl = "",
        uploaderName = "channel-$id",
        uploaderUrl = "https://yt.com/c/$id",
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
}
