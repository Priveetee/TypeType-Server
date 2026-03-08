package dev.typetype.server

import dev.typetype.server.models.LikeItem
import dev.typetype.server.routes.likesRoutes
import dev.typetype.server.services.LikesService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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

class LikesRoutesTest {

    private val service: LikesService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { likesRoutes(service, token) }
        }
        block()
    }

    @Test
    fun `GET likes without token returns 401`() = withApp {
        val response = client.get("/likes")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET likes returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/likes") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST likes returns 201`() = withApp {
        coEvery { service.add(any()) } returns LikeItem(videoUrl = "https://yt.com/v=test")
        val response = client.post("/likes/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE likes returns 204 when found`() = withApp {
        coEvery { service.delete(any()) } returns true
        val response = client.delete("/likes/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE likes returns 404 when not found`() = withApp {
        coEvery { service.delete(any()) } returns false
        val response = client.delete("/likes/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
