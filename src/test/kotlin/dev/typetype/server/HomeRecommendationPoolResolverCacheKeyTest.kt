package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationPoolResolverCacheKeyTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val subscriptions = SubscriptionsService()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedback = RecommendationFeedbackService(eventService)
    private val resolver = HomeRecommendationPoolResolver(
        subscriptionsService = subscriptions,
        subscriptionFeedService = SubscriptionFeedService(subscriptions, channelService, cache),
        historyService = HistoryService(),
        favoritesService = FavoritesService(),
        watchLaterService = WatchLaterService(),
        blockedService = BlockedService(),
        feedbackService = feedback,
        eventService = eventService,
        feedHistoryService = RecommendationFeedHistoryService(),
        trendingService = trendingService,
        searchService = searchService,
        cache = cache,
    )

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
        coEvery { searchService.search(any(), any(), any()) } returns ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))
    }

    @Test
    fun `cache key differs when personalization flag differs`() = runTest {
        val homeKeys = mutableListOf<String>()
        coEvery { cache.get(any()) } answers {
            val key = firstArg<String>()
            if (key.startsWith("recommendations:home:")) homeKeys += key
            null
        }
        resolver.resolve(TEST_USER_ID, 0, personalizationEnabled = true)
        resolver.resolve(TEST_USER_ID, 0, personalizationEnabled = false)
        assertNotEquals(homeKeys.firstOrNull(), homeKeys.lastOrNull())
    }
}
