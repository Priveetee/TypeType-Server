package dev.typetype.server

import dev.typetype.server.SubscriptionFeedTestFixtures.channel
import dev.typetype.server.SubscriptionFeedTestFixtures.subscription
import dev.typetype.server.SubscriptionFeedTestFixtures.video
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionFeedLiveVisibilityRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val settingsService = SettingsService()
    private lateinit var subscriptionsService: SubscriptionsService
    private lateinit var feedService: SubscriptionFeedService

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        subscriptionsService = SubscriptionsService()
        val channelService = mockk<ChannelService>()
        coEvery { channelService.getChannel(any(), null) } returns channel(
            video(4_000L, url = "https://youtube.com/watch?v=live", live = true),
            video(3_500L, url = "https://youtube.com/watch?v=scheduled").copy(
                duration = 0L,
                publishedAt = System.currentTimeMillis() + 86_400_000L,
            ),
            video(3_000L, url = "https://youtube.com/watch?v=normal-1"),
            video(2_000L, url = "https://youtube.com/watch?v=normal-2"),
        )
        feedService = SubscriptionFeedService(subscriptionsService, channelService, FakeCacheService())
    }

    @Test
    fun `account setting hides live streams before pagination`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        settingsService.upsert(TEST_USER_ID, SettingsItem(hideSubscriptionLiveStreams = true))

        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val first = readPage(requestFeed(limit = 1))
        val cursor = requireNotNull(first.nextpage)
        val second = readPage(requestFeed(limit = 1, cursor = cursor))

        assertEquals(listOf("normal-1"), first.videos.map { it.url.substringAfter("v=") })
        assertEquals(listOf("normal-2"), second.videos.map { it.url.substringAfter("v=") })
        assertTrue(second.nextpage == null)
    }

    @Test
    fun `cursor is rejected after live visibility changes`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val cursor = requireNotNull(readPage(requestFeed(limit = 1)).nextpage)

        settingsService.upsert(TEST_USER_ID, SettingsItem(hideSubscriptionLiveStreams = true))

        assertEquals(HttpStatusCode.BadRequest, requestFeed(limit = 1, cursor = cursor).status)
    }

    @Test
    fun `account setting hides finished live recordings`() = withApp {
        val channelService = mockk<ChannelService>()
        coEvery { channelService.getChannel(any(), null) } returns channel(
            video(3_000L, url = "https://youtube.com/watch?v=replay").copy(
                streamType = "post_live_stream",
                isPostLive = true,
            ),
            video(2_500L, url = "https://youtube.com/watch?v=live-content").copy(isLiveContent = true),
            video(2_000L, url = "https://youtube.com/watch?v=normal"),
        )
        feedService = SubscriptionFeedService(subscriptionsService, channelService, FakeCacheService())
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        settingsService.upsert(TEST_USER_ID, SettingsItem(hideSubscriptionLiveStreams = true))

        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 30).status)
        feedService.awaitRefresh(TEST_USER_ID)

        assertEquals(listOf("normal"), readPage(requestFeed(limit = 30)).videos.map { it.url.substringAfter("v=") })
    }

    @Test
    fun `account setting hides unclassified videos from streams tab`() = withApp {
        val channelUrl = "https://www.youtube.com/channel/UC1"
        val channelService = mockk<ChannelService>()
        coEvery { channelService.getChannel(channelUrl, null) } returns channel(
            video(3_000L, url = "https://youtube.com/watch?v=normal"),
            video(2_000L, url = "https://youtube.com/watch?v=replay"),
        )
        coEvery { channelService.getChannel("$channelUrl/streams", null) } returns channel(
            video(2_000L, url = "https://youtube.com/watch?v=replay"),
            video(1_000L, url = "https://youtube.com/watch?v=scheduled"),
        )
        feedService = SubscriptionFeedService(subscriptionsService, channelService, FakeCacheService())
        subscriptionsService.add(TEST_USER_ID, subscription(channelUrl, "Live channel"))
        settingsService.upsert(TEST_USER_ID, SettingsItem(hideSubscriptionLiveStreams = true))

        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 30).status)
        feedService.awaitRefresh(TEST_USER_ID)

        assertEquals(listOf("normal"), readPage(requestFeed(limit = 30)).videos.map { it.url.substringAfter("v=") })
    }

    @Test
    fun `account setting hides members only videos before pagination`() = withApp {
        val channelService = mockk<ChannelService>()
        coEvery { channelService.getChannel(any(), null) } returns channel(
            video(4_000L, url = "https://youtube.com/watch?v=members").copy(requiresMembership = true),
            video(3_000L, url = "https://youtube.com/watch?v=public-1"),
            video(2_000L, url = "https://youtube.com/watch?v=public-2"),
        )
        feedService = SubscriptionFeedService(subscriptionsService, channelService, FakeCacheService())
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        settingsService.upsert(TEST_USER_ID, SettingsItem(hideMembersOnlyContent = true))

        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val first = readPage(requestFeed(limit = 1))
        val second = readPage(requestFeed(limit = 1, cursor = requireNotNull(first.nextpage)))

        assertEquals(listOf("public-1"), first.videos.map { it.url.substringAfter("v=") })
        assertEquals(listOf("public-2"), second.videos.map { it.url.substringAfter("v=") })
        assertTrue(second.nextpage == null)
    }

    @Test
    fun `cursor is rejected after members only visibility changes`() = withApp {
        subscriptionsService.add(TEST_USER_ID, subscription(1))
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feedService.awaitRefresh(TEST_USER_ID)
        val cursor = requireNotNull(readPage(requestFeed(limit = 1)).nextpage)

        settingsService.upsert(TEST_USER_ID, SettingsItem(hideMembersOnlyContent = true))

        assertEquals(HttpStatusCode.BadRequest, requestFeed(limit = 1, cursor = cursor).status)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionFeedRoutes(feedService, auth, settingsService) }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.requestFeed(
        limit: Int,
        cursor: String? = null,
    ): HttpResponse = client.get("/subscriptions/feed") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        parameter("limit", limit)
        cursor?.let { parameter("cursor", it) }
    }

    private suspend fun readPage(response: HttpResponse): SubscriptionFeedResponse {
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }
}
