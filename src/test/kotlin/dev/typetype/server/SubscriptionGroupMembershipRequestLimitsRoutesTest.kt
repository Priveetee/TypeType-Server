package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionGroupsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionGroupMembershipRequestLimitsRoutesTest {
    private val groups = SubscriptionGroupsService()
    private val subscriptions = SubscriptionsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        private const val EXPECTED_BODY_LIMIT_BYTES = 1024 * 1024
        private const val EXPECTED_CHANNEL_URL_LIMIT = 2048

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `membership mutation rejects declared body over one mebibyte`() = withApp {
        val group = createGroup()

        val response = client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("x".repeat(EXPECTED_BODY_LIMIT_BYTES + 1))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `membership mutation rejects streamed body over one mebibyte`() = withApp {
        val group = createGroup()

        val response = client.put("/subscriptions/groups/${group.id}/channels") {
            authorize()
            setBody(oversizedStreamingBody())
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `membership mutation rejects channel urls over 2048 characters`() = withApp {
        val group = createGroup()
        val channelUrl = "https://example.com/" + "a".repeat(EXPECTED_CHANNEL_URL_LIMIT)

        val response = client.put("/subscriptions/groups/${group.id}/channels") {
            authorizeJson()
            setBody("""{"channelUrl":"$channelUrl"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `membership deletion rejects query and whitespace body together`() = withApp {
        val channelUrl = "https://example.com/channel"
        subscriptions.add(TEST_USER_ID, SubscriptionItem(channelUrl, "Channel", ""))
        val group = createGroup()
        groups.addSubscription(TEST_USER_ID, group.id, channelUrl)

        val response = client.delete("/subscriptions/groups/${group.id}/channels") {
            authorize()
            parameter("url", channelUrl)
            header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            setBody("   ")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(1, groups.getAll(TEST_USER_ID).single().channelCount)
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionGroupsRoutes(groups, auth) }
        }
        block()
    }

    private suspend fun createGroup() = requireNotNull(
        (groups.create(TEST_USER_ID, "Work") as? SubscriptionGroupWriteResult.Success)?.group,
    )

    private fun oversizedStreamingBody() = object : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Json

        override suspend fun writeTo(channel: ByteWriteChannel) {
            val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }
            repeat(EXPECTED_BODY_LIMIT_BYTES / chunk.size + 1) { channel.writeByteArray(chunk) }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorizeJson() {
        authorize()
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
