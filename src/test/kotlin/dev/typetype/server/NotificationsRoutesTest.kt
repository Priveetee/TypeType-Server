package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.notificationsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.NotificationsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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

class NotificationsRoutesTest {
    private val channelService: ChannelService = mockk()
    private val cacheService: CacheService = mockk()
    private val subscriptionsService = SubscriptionsService()
    private val subscriptionFeedService = SubscriptionFeedService(subscriptionsService, channelService, cacheService)
    private val notificationsService = NotificationsService(subscriptionFeedService)
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
            routing { notificationsRoutes(notificationsService, auth) }
        }
        block()
    }

    private fun video(uploaded: Long, channel: String): VideoItem = VideoItem(
        id = "id-$uploaded-$channel",
        title = "V$uploaded",
        url = "https://yt.com/watch?v=$uploaded$channel",
        thumbnailUrl = "",
        uploaderName = channel,
        uploaderUrl = "https://yt.com/c/$channel",
        uploaderAvatarUrl = "",
        duration = 300L,
        viewCount = 0L,
        uploadDate = "",
        uploaded = uploaded,
        streamType = "video_stream",
        isShortFormContent = false,
        uploaderVerified = false,
        shortDescription = null,
    )
    private fun channel(vararg videos: VideoItem): ExtractionResult<ChannelResponse> = ExtractionResult.Success(
        ChannelResponse("Test", "", "", "", 0L, false, videos.toList(), null),
    )

    @Test
    fun `GET notifications requires auth`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/notifications").status)
    }

    @Test
    fun `GET notifications returns latest per channel with unread count`() = withApp {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/b", "B", ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(video(1000L, "A"), video(3000L, "A"))
        coEvery { channelService.getChannel("https://yt.com/c/b", null) } returns channel(video(2000L, "B"))
        val body = client.get("/notifications?page=0&limit=10") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(body.contains("\"unreadCount\":2"))
        assertTrue(body.indexOf("3000") < body.indexOf("2000"))
        assertTrue(!body.contains("1000"))
    }

    @Test
    fun `POST notifications read-all clears unread count`() = withApp {
        subscriptionsService.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns channel(video(3000L, "A"))
        val before = client.get("/notifications") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(before.contains("\"unreadCount\":1"))
        val mark = client.post("/notifications/read-all") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, mark.status)
        val after = client.get("/notifications") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()
        assertTrue(after.contains("\"unreadCount\":0"))
    }
}
