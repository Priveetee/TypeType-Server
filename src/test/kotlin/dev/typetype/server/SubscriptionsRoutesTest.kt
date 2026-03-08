package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
import org.junit.jupiter.api.Test

class SubscriptionsRoutesTest {

    private val service: SubscriptionsService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionsRoutes(service, token) }
        }
        block()
    }

    private val itemBody = """{"channelUrl":"https://yt.com/channel/1","name":"Test","avatarUrl":""}"""

    private fun testItem() = SubscriptionItem(channelUrl = "https://yt.com/channel/1", name = "Test", avatarUrl = "")

    @Test
    fun `GET subscriptions without token returns 401`() = withApp {
        val response = client.get("/subscriptions")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET subscriptions returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/subscriptions") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST subscriptions returns 201`() = withApp {
        coEvery { service.add(any()) } returns testItem()
        val response = client.post("/subscriptions") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(itemBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE subscriptions returns 204 when found`() = withApp {
        coEvery { service.delete(any()) } returns true
        val response = client.delete("/subscriptions/https%3A%2F%2Fyt.com%2Fchannel%2F1") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE subscriptions returns 404 when not found`() = withApp {
        coEvery { service.delete(any()) } returns false
        val response = client.delete("/subscriptions/https%3A%2F%2Fyt.com%2Fchannel%2F1") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
