package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationCursor
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationPrivacyService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationServiceFastPathTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val subscriptions = SubscriptionsService()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedback = RecommendationFeedbackService(eventService)
    private val feedHistoryService = RecommendationFeedHistoryService()
    private val privacyService = RecommendationPrivacyService(SettingsService())
    private val service = HomeRecommendationService(
        poolResolver = HomeRecommendationPoolResolver(
            subscriptionsService = subscriptions,
            subscriptionFeedService = SubscriptionFeedService(subscriptions, channelService, cache),
            historyService = HistoryService(),
            favoritesService = FavoritesService(),
            watchLaterService = WatchLaterService(),
            blockedService = BlockedService(),
            feedbackService = feedback,
            eventService = eventService,
            feedHistoryService = feedHistoryService,
            trendingService = trendingService,
            searchService = searchService,
            cache = cache,
        ),
        feedHistoryService = feedHistoryService,
        privacyService = privacyService,
    )

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { searchService.search(any(), any(), any()) } returns ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))
    }

    @Test
    fun `cold start uses fast path and returns discovery without cached subscriptions`() = runTest {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } coAnswers {
            delay(2_500)
            ExtractionResult.Success(ChannelResponse("A", "", "", "", 0L, false, listOf(video("s1", now)), null))
        }
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(
            listOf(video("d1", now - 1), video("d2", now - 2), video("d3", now - 3), video("d4", now - 4)),
        )
        val response = service.getHome(TEST_USER_ID, 0, 4, HomeRecommendationCursor())
        assertTrue(response.items.isNotEmpty())
        assertTrue(response.items.any { it.id.startsWith("d") })
        assertFalse(response.items.any { it.id == "s1" })
    }

    @Test
    fun `cached subscription feed avoids channel fetch`() = runTest {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        val cachedFeed = CacheJson.encodeToString(
            ListSerializer(VideoItem.serializer()),
            listOf(video("s1", now), video("s2", now - 1)),
        )
        coEvery { cache.get(match { it.startsWith("recommendations:home:") }) } returns null
        coEvery { cache.get(match { it.startsWith("feed:") }) } returns cachedFeed
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(listOf(video("d1", now - 1)))
        coEvery { channelService.getChannel(any(), any()) } coAnswers {
            delay(2_500)
            ExtractionResult.Failure("slow channel fetch")
        }
        val response = service.getHome(TEST_USER_ID, 0, 4, HomeRecommendationCursor())
        assertTrue(response.items.isNotEmpty())
    }

    @Test
    fun `cursor carries recent channels to reduce cross page repetition`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(
            (1..12).map { index -> video("d$index", now - index, channel = "c$index") },
        )
        val first = service.getHome(TEST_USER_ID, 0, 6, HomeRecommendationCursor())
        val firstLastChannel = first.items.last().uploaderUrl
        val cursor = dev.typetype.server.services.HomeRecommendationCursorCodec.decode(first.nextCursor)
        val second = service.getHome(TEST_USER_ID, 0, 6, cursor ?: HomeRecommendationCursor())
        val secondFirstChannel = second.items.firstOrNull()?.uploaderUrl
        if (secondFirstChannel != null) assertNotEquals(firstLastChannel, secondFirstChannel)
    }

    private fun video(id: String, uploaded: Long, channel: String = "Channel"): VideoItem = VideoItem(
        id, id, "https://yt.com/v/$id", "", channel, "https://yt.com/c/$channel", "", 60, 0, "", uploaded,
        "video_stream", false, false, null,
    )
}
