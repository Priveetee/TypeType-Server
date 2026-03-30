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

class SubscriptionShortsFeedRoutesTest {
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

    private fun video(uploaded: Long, url: String, short: Boolean) = VideoItem(
        id = "id-$uploaded", title = "V$uploaded", url = url, thumbnailUrl = "",
        uploaderName = "Ch", uploaderUrl = "chUrl", uploaderAvatarUrl = "",
        duration = 40L, viewCount = 0L, uploadDate = "", uploaded = uploaded,
        streamType = "video_stream", isShortFormContent = short, uploaderVerified = false, shortDescription = null,
    )

    private fun channel(vararg v: VideoItem) = ExtractionResult.Success(
        ChannelResponse("Test", "", "", "", 0L, false, v.toList(), null),
    )

    @Test
    fun `GET subscriptions shorts returns only short videos deduped and sorted`() = withApp {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/1", "C1", ""))
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/2", "C2", ""))
        coEvery { channelService.getChannel("https://yt.com/c/1/shorts", null) } returns channel(
            video(3000L, "https://yt.com/shorts/a", short = true),
        )
        coEvery { channelService.getChannel("https://yt.com/c/2/shorts", null) } returns channel(
            video(2000L, "https://yt.com/shorts/b", short = true),
            video(1500L, "https://yt.com/shorts/b", short = true),
        )
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } returns channel(video(1000L, "https://yt.com/watch?v=1", short = false))
        coEvery { channelService.getChannel("https://yt.com/c/2", null) } returns channel(video(1000L, "https://yt.com/watch?v=2", short = false))

        val body = client.get("/subscriptions/shorts?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()

        assertTrue(body.contains("shorts/a"))
        assertTrue(body.contains("shorts/b"))
        assertTrue(!body.contains("watch?v=1"))
        assertTrue(body.indexOf("3000") < body.indexOf("2000"))
    }

    @Test
    fun `GET subscriptions shorts pagination and auth work`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions/shorts").status)
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/1", "C1", ""))
        coEvery { channelService.getChannel("https://yt.com/c/1/shorts", null) } returns channel(
            video(3000L, "https://yt.com/shorts/a", short = true),
            video(2000L, "https://yt.com/shorts/b", short = true),
            video(1000L, "https://yt.com/shorts/c", short = true),
        )
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } returns channel(
            video(3000L, "https://yt.com/shorts/a", short = true),
            video(2000L, "https://yt.com/shorts/b", short = true),
            video(1000L, "https://yt.com/shorts/c", short = true),
        )

        val page1 = client.get("/subscriptions/shorts?page=0&limit=2") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        val page2 = client.get("/subscriptions/shorts?page=1&limit=2") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()

        assertTrue(page1.contains("shorts/a") && page1.contains("shorts/b") && !page1.contains("shorts/c"))
        assertTrue(page1.contains("\"nextpage\":\"1\""))
        assertTrue(page2.contains("shorts/c"))
    }
}
