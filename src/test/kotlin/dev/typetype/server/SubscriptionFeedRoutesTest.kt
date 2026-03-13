package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TokenService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
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

class SubscriptionFeedRoutesTest {

    private val channelService: ChannelService = mockk()
    private val cacheService: CacheService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val feedService = SubscriptionFeedService(subscriptionsService, channelService, cacheService)
    private val token = "test-token"

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach fun clean() {
        TestDatabase.truncateAll()
        coEvery { cacheService.get(any()) } returns null
        coEvery { cacheService.set(any(), any(), any()) } returns Unit
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionFeedRoutes(feedService, TokenService.fixed(token)) }
        }
        block()
    }

    private fun video(uploaded: Long) = VideoItem(
        id = "id-$uploaded", title = "V$uploaded", url = "u/$uploaded", thumbnailUrl = "",
        uploaderName = "Ch", uploaderUrl = "chUrl", uploaderAvatarUrl = "",
        duration = 300L, viewCount = 0L, uploadDate = "", uploaded = uploaded,
        streamType = "video_stream", isShortFormContent = false, uploaderVerified = false, shortDescription = null,
    )

    private fun channel(vararg v: VideoItem) = ExtractionResult.Success(
        ChannelResponse("Test", "", "", "", 0L, false, v.toList(), null),
    )

    private fun sub(n: Int) = SubscriptionItem("https://yt.com/c/$n", "C$n", "")

    @Test
    fun `GET subscriptions feed without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions/feed").status)
    }

    @Test
    fun `GET subscriptions feed with no subscriptions returns empty`() = withApp {
        val body = client.get("/subscriptions/feed") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"videos\":[]"))
    }

    @Test
    fun `GET subscriptions feed returns videos sorted by uploaded desc`() = withApp {
        subscriptionsService.add(sub(1))
        subscriptionsService.add(sub(2))
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } returns channel(video(1000L), video(3000L))
        coEvery { channelService.getChannel("https://yt.com/c/2", null) } returns channel(video(2000L))
        val body = client.get("/subscriptions/feed?page=0&limit=10") {
            headers.append("X-Instance-Token", token)
        }.bodyAsText()
        assertTrue(body.indexOf("3000") < body.indexOf("2000"))
        assertTrue(body.indexOf("2000") < body.indexOf("1000"))
    }

    @Test
    fun `GET subscriptions feed pagination works`() = withApp {
        subscriptionsService.add(sub(1))
        coEvery { channelService.getChannel(any(), null) } returns channel(video(5000L), video(4000L), video(3000L))
        val body = client.get("/subscriptions/feed?page=0&limit=2") {
            headers.append("X-Instance-Token", token)
        }.bodyAsText()
        assertTrue(body.contains("5000") && body.contains("4000") && !body.contains("3000"))
        assertTrue(body.contains("\"nextpage\":\""))
    }

    @Test
    fun `GET subscriptions feed failed channel is silently ignored`() = withApp {
        subscriptionsService.add(sub(1))
        subscriptionsService.add(sub(2))
        coEvery { channelService.getChannel("https://yt.com/c/1", null) } returns channel(video(1000L))
        coEvery { channelService.getChannel("https://yt.com/c/2", null) } returns ExtractionResult.Failure("err")
        val resp = client.get("/subscriptions/feed") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("1000"))
    }

    @Test
    fun `GET subscriptions feed last page has null nextpage`() = withApp {
        subscriptionsService.add(sub(1))
        coEvery { channelService.getChannel(any(), null) } returns channel(video(1000L))
        val body = client.get("/subscriptions/feed") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"nextpage\":null"))
    }
}
