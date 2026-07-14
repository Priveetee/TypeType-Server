package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.subscriptionShortsFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionShortsBlendService
import dev.typetype.server.services.SubscriptionShortsFeedService
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

class SubscriptionShortsBlendedFeedRoutesTest {
    private val channelService: ChannelService = mockk()
    private val cacheService: CacheService = mockk()
    private val trendingService: TrendingService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val feedService = SubscriptionShortsFeedService(
        subscriptionsService,
        channelService,
        SubscriptionShortsBlendService(trendingService),
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

    @Test
    fun `GET subscriptions shorts blended includes trending discovery`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        coEvery { channelService.getChannel("https://yt.com/c/1/shorts", null) } returns channel(
            video(3000L, url = "https://yt.com/watch?v=sa", short = false),
        )
        coEvery { trendingService.getTrending(0) } returns ExtractionResult.Success(
            listOf(video(2500L, url = "https://yt.com/watch?v=trend", short = true).copy(id = "trend", uploaderUrl = "trendChannel")),
        )
        val body = client.get("/subscriptions/shorts?page=0&limit=3&blended=true&service=0") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("/shorts/sa"))
        assertTrue(body.contains("/shorts/trend"))
    }
}
