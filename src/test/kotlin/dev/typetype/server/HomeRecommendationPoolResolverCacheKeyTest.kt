package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolMode
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.HomeRecommendationContext
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationSessionContext
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
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
    private val resolverDeps = homeResolverDependencies(
        subscriptions = subscriptions,
        channelService = channelService,
        cache = cache,
        feedbackService = feedback,
        eventService = eventService,
        feedHistoryService = RecommendationFeedHistoryService(),
        trendingService = trendingService,
        searchService = searchService,
    )
    private val resolver = buildHomeResolver(resolverDeps)

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
        resolver.resolve(TEST_USER_ID, 0, HomeRecommendationPoolMode.FULL, personalizationEnabled = true, context = context())
        resolver.resolve(TEST_USER_ID, 0, HomeRecommendationPoolMode.FULL, personalizationEnabled = false, context = context())
        assertNotEquals(homeKeys.firstOrNull(), homeKeys.lastOrNull())
    }

    private fun context() = defaultContext()
}
