package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService
import kotlinx.coroutines.runBlocking
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationServiceTest {

    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val subscriptions = SubscriptionsService()
    private val history = HistoryService()
    private val favorites = FavoritesService()
    private val watchLater = WatchLaterService()
    private val blocked = BlockedService()
    private val feed = SubscriptionFeedService(subscriptions, channelService, cache)
    private val service = HomeRecommendationService(subscriptions, feed, history, favorites, watchLater, blocked, trendingService, cache)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
    }

    private fun video(id: String, uploaded: Long, url: String = "https://yt.com/v/$id", channel: String = "https://yt.com/c/a"): VideoItem = VideoItem(
        id = id,
        title = id,
        url = url,
        thumbnailUrl = "",
        uploaderName = "A",
        uploaderUrl = channel,
        uploaderAvatarUrl = "",
        duration = 10,
        viewCount = 0,
        uploadDate = "",
        uploaded = uploaded,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `getHome filters blocked and seen urls`() = runBlocking {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://yt.com/c/a", name = "A", avatarUrl = ""))
        val seen = "https://yt.com/v/seen"
        val blockedUrl = "https://yt.com/v/blocked"
        history.add(TEST_USER_ID, HistoryItem(url = seen, title = "s", thumbnail = "", channelName = "A", channelUrl = "https://yt.com/c/a", duration = 1, progress = 1))
        blocked.addVideo(TEST_USER_ID, blockedUrl)
        val items = listOf(
            video("seen", now - 1_000, url = seen),
            video("blocked", now - 2_000, url = blockedUrl),
            video("ok", now - 3_000, url = "https://yt.com/v/ok"),
        )
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns ExtractionResult.Success(
            ChannelResponse("A", "", "", "", 0L, false, items, null)
        )
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
        val response = service.getHome(userId = TEST_USER_ID, serviceId = 0, limit = 20, index = 0)
        assertEquals(1, response.items.size)
        assertEquals("https://yt.com/v/ok", response.items.first().url)
    }

    @Test
    fun `getHome paginates with hasMore and nextCursor`() = runBlocking {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://yt.com/c/a", name = "A", avatarUrl = ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns ExtractionResult.Success(
            ChannelResponse(
                "A",
                "",
                "",
                "",
                0L,
                false,
                listOf(video("v1", now - 1_000), video("v2", now - 2_000), video("v3", now - 3_000)),
                null,
            )
        )
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
        val response = service.getHome(userId = TEST_USER_ID, serviceId = 0, limit = 2, index = 0)
        assertEquals(2, response.items.size)
        assertTrue(response.hasMore)
        assertTrue(!response.nextCursor.isNullOrBlank())
    }
}
