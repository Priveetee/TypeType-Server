package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TokenService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsRoutesTest {

    private val service = SubscriptionsService()
    private val token = "test-token"

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionsRoutes(service, TokenService.fixed(token)) }
        }
        block()
    }

    private val itemBody = """{"channelUrl":"https://yt.com/channel/1","name":"Test","avatarUrl":""}"""

    @Test
    fun `GET subscriptions without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/subscriptions").status)
    }

    @Test
    fun `GET subscriptions returns 200 with empty list`() = withApp {
        val response = client.get("/subscriptions") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST subscriptions returns 201 and persists item`() = withApp {
        val response = client.post("/subscriptions") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(itemBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"channelUrl\":\"https://yt.com/channel/1\""))
    }

    @Test
    fun `GET subscriptions returns persisted items`() = withApp {
        service.add(SubscriptionItem(channelUrl = "https://yt.com/channel/1", name = "Test", avatarUrl = ""))
        val body = client.get("/subscriptions") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"channelUrl\":\"https://yt.com/channel/1\""))
    }

    @Test
    fun `DELETE subscriptions returns 204 when found`() = withApp {
        service.add(SubscriptionItem(channelUrl = "https://yt.com/channel/1", name = "Test", avatarUrl = ""))
        assertEquals(HttpStatusCode.NoContent, client.delete("/subscriptions/https%3A%2F%2Fyt.com%2Fchannel%2F1") { headers.append("X-Instance-Token", token) }.status)
    }

    @Test
    fun `DELETE subscriptions returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.delete("/subscriptions/https%3A%2F%2Fyt.com%2Fchannel%2F1") { headers.append("X-Instance-Token", token) }.status)
    }
}
