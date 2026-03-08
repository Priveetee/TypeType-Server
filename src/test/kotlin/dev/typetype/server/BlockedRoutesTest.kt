package dev.typetype.server

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.services.BlockedService
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

class BlockedRoutesTest {

    private val service: BlockedService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { blockedRoutes(service, token) }
        }
        block()
    }

    private val urlBody = """{"url":"https://yt.com"}"""
    private fun testItem() = BlockedItem(url = "https://yt.com")

    @Test
    fun `GET blocked-channels without token returns 401`() = withApp {
        val response = client.get("/blocked/channels")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET blocked-channels returns 200`() = withApp {
        coEvery { service.getChannels() } returns emptyList()
        val response = client.get("/blocked/channels") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST blocked-channels returns 201`() = withApp {
        coEvery { service.addChannel(any()) } returns testItem()
        val response = client.post("/blocked/channels") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(urlBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE blocked-channels returns 204 when found`() = withApp {
        coEvery { service.deleteChannel(any()) } returns true
        val response = client.delete("/blocked/channels/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET blocked-videos returns 200`() = withApp {
        coEvery { service.getVideos() } returns emptyList()
        val response = client.get("/blocked/videos") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST blocked-videos returns 201`() = withApp {
        coEvery { service.addVideo(any()) } returns testItem()
        val response = client.post("/blocked/videos") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(urlBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE blocked-videos returns 204 when found`() = withApp {
        coEvery { service.deleteVideo(any()) } returns true
        val response = client.delete("/blocked/videos/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE blocked-videos returns 404 when not found`() = withApp {
        coEvery { service.deleteVideo(any()) } returns false
        val response = client.delete("/blocked/videos/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
