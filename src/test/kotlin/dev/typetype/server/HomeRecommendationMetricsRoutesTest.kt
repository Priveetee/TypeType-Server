package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.homeRecommendationMetricsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.RecommendationPrivacyService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationMetricsRoutesTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedback = RecommendationFeedbackService(eventService)
    private val feedHistoryService = RecommendationFeedHistoryService()
    private val privacyService = RecommendationPrivacyService(SettingsService())
    private val resolverDeps = homeResolverDependencies(
        subscriptions = SubscriptionsService(),
        channelService = channelService,
        cache = cache,
        feedbackService = feedback,
        eventService = eventService,
        feedHistoryService = feedHistoryService,
        trendingService = trendingService,
        searchService = searchService,
    )
    private val service = HomeRecommendationService(
        poolResolver = buildHomeResolver(resolverDeps),
        feedHistoryService = feedHistoryService,
        privacyService = privacyService,
    )
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { searchService.search(any(), any(), any()) } returns ExtractionResult.Success(
            SearchPageResponse(emptyList(), null, null, false),
        )
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(
            listOf(video("a", "c1"), video("b", "c2"), video("a", "c1")),
        )
    }

    @Test
    fun `metrics route returns 400 without clicked urls`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationMetricsRoutes(service, auth) }
        }
        val response = client.get("/recommendations/home/metrics") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `metrics route returns evaluation payload`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationMetricsRoutes(service, auth) }
        }
        val response = client.get("/recommendations/home/metrics?clicked=https://yt.com/v/a") {
            header(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ndcgAt10"))
        assertTrue(body.contains("diversityAt10"))
        assertTrue(body.contains("duplicateRateAt10"))
    }

    private fun video(id: String, channel: String): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        uploaded = 1L,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
}
