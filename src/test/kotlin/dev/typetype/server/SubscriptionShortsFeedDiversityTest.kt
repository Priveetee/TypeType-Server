package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.subscriptionShortsFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionShortsSignalService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionShortsFeedDiversityTest {
    private val channelService: ChannelService = mockk()
    private val cacheService: CacheService = mockk()
    private val trendingService: TrendingService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedService = SubscriptionShortsFeedService(
        subscriptionsService,
        channelService,
        SubscriptionShortsBlendService(trendingService, SubscriptionShortsSignalService(eventService)),
        cacheService,
    )
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cacheService.get(any()) } returns null
        coEvery { cacheService.set(any(), any(), any()) } returns Unit
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(emptyList())
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionShortsFeedRoutes(feedService, auth) }
        }
        block()
    }

    private fun short(uploader: String, id: String) = VideoItem(
        id = id, title = id, url = "https://yt.com/watch?v=$id", thumbnailUrl = "", uploaderName = uploader,
        uploaderUrl = uploader, uploaderAvatarUrl = "", duration = 10, viewCount = 0, uploadDate = "", uploaded = -1,
        streamType = "video_stream", isShortFormContent = false, uploaderVerified = false, shortDescription = null,
    )

    @Test
    fun `feed is diversified when one uploader dominates`() = withApp {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/A", "A", ""))
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/B", "B", ""))
        coEvery { channelService.getChannel("https://yt.com/A/shorts", null) } returns ExtractionResult.Success(
            ChannelResponse("A", "", "", "", 0, false, listOf(short("A", "a1"), short("A", "a2"), short("A", "a3")), null),
        )
        coEvery { channelService.getChannel("https://yt.com/B/shorts", null) } returns ExtractionResult.Success(
            ChannelResponse("B", "", "", "", 0, false, listOf(short("B", "b1")), null),
        )
        val body = client.get("/subscriptions/shorts?page=0&limit=4") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("/shorts/a1"))
        assertTrue(body.contains("/shorts/b1"))
        assertTrue(body.indexOf("/shorts/b1") < body.indexOf("/shorts/a3"))
    }
}
