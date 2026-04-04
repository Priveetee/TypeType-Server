package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.homeRecommendationRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.RecommendationPrivacyService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationRoutesKillSwitchTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val settings = SettingsService()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedback = RecommendationFeedbackService(eventService)
    private val feedHistory = RecommendationFeedHistoryService()
    private val service = HomeRecommendationService(
        poolResolver = HomeRecommendationPoolResolver(
            subscriptionsService = SubscriptionsService(),
            subscriptionFeedService = SubscriptionFeedService(SubscriptionsService(), channelService, cache),
            historyService = HistoryService(),
            favoritesService = FavoritesService(),
            watchLaterService = WatchLaterService(),
            blockedService = BlockedService(),
            feedbackService = feedback,
            eventService = eventService,
            feedHistoryService = feedHistory,
            trendingService = trendingService,
            searchService = searchService,
            cache = cache,
        ),
        feedHistoryService = feedHistory,
        privacyService = RecommendationPrivacyService(settings),
    )
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        kotlinx.coroutines.runBlocking {
            settings.upsert(TEST_USER_ID, SettingsItem(recommendationPersonalizationEnabled = false))
        }
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
        coEvery { searchService.search(any(), any(), any()) } returns ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))
    }

    @Test
    fun `home route still returns 200 when personalization disabled`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationRoutes(service, auth) }
        }
        val response = client.get("/recommendations/home") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
