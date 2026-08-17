package dev.typetype.server

import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionFeedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionGroupsService
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionGroupFeedRoutesTest {
    private val subscriptions = SubscriptionsService()
    private val groups = SubscriptionGroupsService()
    private lateinit var feed: SubscriptionFeedService
    private lateinit var cache: FakeCacheService
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        cache = FakeCacheService()
        feed = SubscriptionFeedService(subscriptions, FakeChannelService(), cache)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionFeedRoutes(feed, auth, groupsService = groups) }
        }
        block()
    }

    @Test
    fun `group and ungrouped feeds project one shared global snapshot`() = withApp {
        subscriptions.add(TEST_USER_ID, subscription("one"))
        subscriptions.add(TEST_USER_ID, subscription("two"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        assertEquals(
            SubscriptionGroupMembershipResult.Success,
            groups.addSubscription(TEST_USER_ID, group.id, channel("one")),
        )

        assertEquals(HttpStatusCode.Accepted, requestFeed(groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(listOf("${channel("one")}/video"), requestReadyFeed(groupId = group.id).videos.map { it.url })
        assertEquals(listOf("${channel("two")}/video"), requestReadyFeed(ungrouped = true).videos.map { it.url })
        assertEquals(2, requestReadyFeed().videos.size)
    }

    @Test
    fun `cursor keeps its original group membership across pages`() = withApp {
        val channelService = mockk<dev.typetype.server.services.ChannelService>()
        coEvery { channelService.getChannel(channel("one"), null) } returns SubscriptionFeedTestFixtures.channel(
            SubscriptionFeedTestFixtures.video(3_000L, channel = "one", url = "video-one"),
        )
        coEvery { channelService.getChannel(channel("two"), null) } returns SubscriptionFeedTestFixtures.channel(
            SubscriptionFeedTestFixtures.video(2_000L, channel = "two", url = "video-two"),
        )
        coEvery { channelService.getChannel(channel("three"), null) } returns SubscriptionFeedTestFixtures.channel(
            SubscriptionFeedTestFixtures.video(1_000L, channel = "three", url = "video-three"),
        )
        feed = SubscriptionFeedService(subscriptions, channelService, cache)
        listOf("one", "two", "three").forEach { subscriptions.add(TEST_USER_ID, subscription(it)) }
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, channel("one"))
        groups.addSubscription(TEST_USER_ID, group.id, channel("two"))
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1, groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)
        val firstPage = requestReadyFeed(limit = 1, groupId = group.id)
        assertEquals(listOf("video-one"), firstPage.videos.map { it.url })
        val repeatedFirstPage = requestReadyFeed(limit = 1, groupId = group.id)
        assertEquals(firstPage.nextpage, repeatedFirstPage.nextpage)
        assertEquals(1, cache.keys().count { it.startsWith("feed:selection") })

        groups.removeSubscription(TEST_USER_ID, group.id, channel("two"))
        groups.addSubscription(TEST_USER_ID, group.id, channel("three"))
        val changedFirstPage = requestReadyFeed(limit = 1, groupId = group.id)
        assertNotEquals(firstPage.nextpage, changedFirstPage.nextpage)
        assertEquals(2, cache.keys().count { it.startsWith("feed:selection") })
        val secondPage = requestFeed(limit = 1, cursor = requireNotNull(firstPage.nextpage), groupId = group.id)

        assertEquals(HttpStatusCode.OK, secondPage.status)
        assertEquals(listOf("video-two"), Json.decodeFromString<SubscriptionFeedResponse>(secondPage.bodyAsText()).videos.map { it.url })
    }

    @Test
    fun `terminal filtered page does not retain a membership snapshot`() = withApp {
        subscriptions.add(TEST_USER_ID, subscription("one"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, channel("one"))
        assertEquals(HttpStatusCode.Accepted, requestFeed(groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(1, requestReadyFeed(groupId = group.id).videos.size)

        assertTrue(cache.keys().none { it.startsWith("feed:selection") })
    }

    @Test
    fun `cursor cannot be reused with another subscription filter`() = withApp {
        subscriptions.add(TEST_USER_ID, subscription("one"))
        subscriptions.add(TEST_USER_ID, subscription("two"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, channel("one"))
        assertEquals(HttpStatusCode.Accepted, requestFeed(limit = 1).status)
        feed.awaitRefresh(TEST_USER_ID)
        val cursor = requireNotNull(requestReadyFeed(limit = 1).nextpage)

        val response = requestFeed(limit = 1, cursor = cursor, groupId = group.id)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("subscription_feed_invalid_cursor"))
    }

    @Test
    fun `group feed follows the fetched subscription source when uploader url differs`() = withApp {
        val sourceUrl = channel("one")
        subscriptions.add(TEST_USER_ID, subscription("one"))
        val group = (groups.create(TEST_USER_ID, "Work") as SubscriptionGroupWriteResult.Success).group
        groups.addSubscription(TEST_USER_ID, group.id, sourceUrl)
        val channelService = mockk<dev.typetype.server.services.ChannelService>()
        coEvery { channelService.getChannel(sourceUrl, null) } returns SubscriptionFeedTestFixtures.channel(
            SubscriptionFeedTestFixtures.video(1_000L, channel = "different-canonical-uploader"),
        )
        feed = SubscriptionFeedService(subscriptions, channelService, FakeCacheService())

        assertEquals(HttpStatusCode.Accepted, requestFeed(groupId = group.id).status)
        feed.awaitRefresh(TEST_USER_ID)

        assertEquals(1, requestReadyFeed(groupId = group.id).videos.size)
    }

    private suspend fun ApplicationTestBuilder.requestReadyFeed(
        limit: Int = 30,
        groupId: String? = null,
        ungrouped: Boolean = false,
    ): SubscriptionFeedResponse {
        val response = requestFeed(limit = limit, groupId = groupId, ungrouped = ungrouped)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.requestFeed(
        limit: Int = 30,
        cursor: String? = null,
        groupId: String? = null,
        ungrouped: Boolean = false,
    ): HttpResponse = client.get("/subscriptions/feed") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        parameter("limit", limit)
        cursor?.let { parameter("cursor", it) }
        groupId?.let { parameter("groupId", it) }
        if (ungrouped) parameter("ungrouped", true)
    }

    private fun subscription(id: String) = SubscriptionItem(channel(id), id, "")

    private fun channel(id: String) = "https://example.com/channel/$id"
}
