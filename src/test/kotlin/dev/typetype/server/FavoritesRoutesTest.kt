package dev.typetype.server

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.services.FavoritesService
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

class FavoritesRoutesTest {

    private val service: FavoritesService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { favoritesRoutes(service, token) }
        }
        block()
    }

    @Test
    fun `GET favorites without token returns 401`() = withApp {
        val response = client.get("/favorites")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET favorites returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/favorites") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST favorites returns 201`() = withApp {
        coEvery { service.add(any()) } returns FavoriteItem(videoUrl = "https://yt.com/v=test")
        val response = client.post("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE favorites returns 204 when found`() = withApp {
        coEvery { service.delete(any()) } returns true
        val response = client.delete("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE favorites returns 404 when not found`() = withApp {
        coEvery { service.delete(any()) } returns false
        val response = client.delete("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
