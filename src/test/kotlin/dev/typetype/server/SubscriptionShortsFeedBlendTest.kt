package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionShortsFeedBlendTest {
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val cacheService: CacheService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val service = SubscriptionShortsFeedService(subscriptionsService, channelService, SubscriptionShortsBlendService(trendingService), cacheService)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cacheService.get(any()) } returns null
        coEvery { cacheService.set(any(), any(), any()) } returns Unit
    }

    @Test
    fun `blended feed includes discovery shorts with subscription shorts`() = runTest {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/sub", "Sub", ""))
        coEvery { channelService.getChannel("https://yt.com/c/sub/shorts", null) } returns ExtractionResult.Success(
            ChannelResponse("Sub", "", "", "", 0, false, listOf(video("s1", "https://yt.com/c/sub")), null),
        )
        coEvery { trendingService.getTrending(0) } returns ExtractionResult.Success(
            listOf(video("d1", "https://yt.com/c/discovery"), video("d2", "https://yt.com/c/discovery2")),
        )
        val feed = service.getBlendedFeed(TEST_USER_ID, 0, 0, 3)
        assertTrue(feed.videos.any { it.url.endsWith("/shorts/s1") })
        assertTrue(feed.videos.any { it.url.endsWith("/shorts/d1") || it.url.endsWith("/shorts/d2") })
    }

    private fun video(id: String, uploaderUrl: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/watch?v=$id",
        thumbnailUrl = "",
        uploaderName = id,
        uploaderUrl = uploaderUrl,
        uploaderAvatarUrl = "",
        duration = 20,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = true,
        uploaderVerified = false,
        shortDescription = null,
    )
}
