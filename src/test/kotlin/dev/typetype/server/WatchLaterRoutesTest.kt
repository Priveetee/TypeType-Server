package dev.typetype.server

import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.WatchLaterService
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

class WatchLaterRoutesTest {

    private val service: WatchLaterService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { watchLaterRoutes(service, token) }
        }
        block()
    }

    private val itemBody = """{"url":"https://yt.com","title":"Test","thumbnail":"","duration":100}"""

    private fun testItem() = WatchLaterItem(url = "https://yt.com", title = "Test", thumbnail = "", duration = 100L)

    @Test
    fun `GET watch-later without token returns 401`() = withApp {
        val response = client.get("/watch-later")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET watch-later returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/watch-later") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST watch-later returns 201`() = withApp {
        coEvery { service.add(any()) } returns testItem()
        val response = client.post("/watch-later") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(itemBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE watch-later returns 204 when found`() = withApp {
        coEvery { service.delete(any()) } returns true
        val response = client.delete("/watch-later/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE watch-later returns 404 when not found`() = withApp {
        coEvery { service.delete(any()) } returns false
        val response = client.delete("/watch-later/https%3A%2F%2Fyt.com") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
