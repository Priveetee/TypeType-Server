package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.subscriptionShortsFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionShortsFeedService
import dev.typetype.server.services.SubscriptionsService
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

class SubscriptionShortsFeedFallbackTest {
    private val channelService: ChannelService = mockk()
    private val cacheService: CacheService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val feedService = SubscriptionShortsFeedService(subscriptionsService, channelService, cacheService)
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cacheService.get(any()) } returns null
        coEvery { cacheService.set(any(), any(), any()) } returns Unit
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionShortsFeedRoutes(feedService, auth) }
        }
        block()
    }

    private fun video(url: String, duration: Long) = VideoItem(
        id = url,
        title = "v",
        url = url,
        thumbnailUrl = "",
        uploaderName = "u",
        uploaderUrl = "ch",
        uploaderAvatarUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        uploaded = duration,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )

    @Test
    fun `fallback duration heuristic keeps short videos when short flag is missing`() = withApp {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/1", "C1", ""))
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } returns ExtractionResult.Success(
            ChannelResponse("Test", "", "", "", 0L, false, listOf(
                video("https://yt.com/watch?v=a", 58),
                video("https://yt.com/watch?v=b", 140),
                video("https://yt.com/watch?v=c", 540),
            ), null),
        )
        coEvery { channelService.getChannel("https://yt.com/c/1/shorts", null) } returns ExtractionResult.Failure("no shorts tab")
        val body = client.get("/subscriptions/shorts?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("watch?v=a"))
        assertTrue(body.contains("watch?v=b"))
        assertTrue(!body.contains("watch?v=c"))
    }
}
