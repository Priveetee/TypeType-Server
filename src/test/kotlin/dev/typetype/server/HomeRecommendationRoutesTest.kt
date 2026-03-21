package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.homeRecommendationRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import dev.typetype.server.services.WatchLaterService
import dev.typetype.server.services.SubscriptionFeedService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationRoutesTest {

    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val historyService = HistoryService()
    private val favoritesService = FavoritesService()
    private val watchLaterService = WatchLaterService()
    private val blockedService = BlockedService()
    private val feedService = SubscriptionFeedService(subscriptionsService, channelService, cache)
    private val recommendationService = HomeRecommendationService(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = feedService,
        historyService = historyService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        trendingService = trendingService,
        cache = cache,
    )
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationRoutes(recommendationService, auth) }
        }
        block()
    }

    private fun video(id: String, uploaded: Long, url: String = "https://yt.com/v/$id", channel: String = "https://yt.com/c/a"): VideoItem = VideoItem(
        id = id,
        title = id,
        url = url,
        thumbnailUrl = "",
        uploaderName = "Channel",
        uploaderUrl = channel,
        uploaderAvatarUrl = "",
        duration = 60,
        viewCount = 0,
        uploadDate = "",
        uploaded = uploaded,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `GET recommendations home without token returns 401`() = withApp {
        val response = client.get("/recommendations/home")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET recommendations home returns items and cursor`() = withApp {
        val now = System.currentTimeMillis()
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem(channelUrl = "https://yt.com/c/a", name = "A", avatarUrl = ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns ExtractionResult.Success(
            ChannelResponse("A", "", "", "", 0L, false, listOf(video("v1", now - 10_000), video("v2", now - 20_000), video("v3", now - 30_000)), null)
        )
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(listOf(video("t1", now - 40_000, url = "https://yt.com/v/t1", channel = "https://yt.com/c/t")))
        val response = client.get("/recommendations/home?limit=2") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"items\""))
        assertTrue(body.contains("\"nextCursor\":\""))
    }
}
