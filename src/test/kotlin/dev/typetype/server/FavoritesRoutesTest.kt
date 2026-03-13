package dev.typetype.server

import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.TokenService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
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

class FavoritesRoutesTest {

    private val service = FavoritesService()
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
            routing { favoritesRoutes(service, TokenService.fixed(token)) }
        }
        block()
    }

    @Test
    fun `GET favorites without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/favorites").status)
    }

    @Test
    fun `GET favorites returns 200 with empty list`() = withApp {
        val response = client.get("/favorites") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST favorites returns 201 and persists item`() = withApp {
        val response = client.post("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"videoUrl\""))
    }

    @Test
    fun `GET favorites returns persisted items`() = withApp {
        service.add("https://yt.com/v=test")
        val body = client.get("/favorites") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"videoUrl\":\"https://yt.com/v=test\""))
    }

    @Test
    fun `DELETE favorites returns 204 when found`() = withApp {
        service.add("https://yt.com/v=test")
        assertEquals(HttpStatusCode.NoContent, client.delete("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") { headers.append("X-Instance-Token", token) }.status)
    }

    @Test
    fun `DELETE favorites returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.delete("/favorites/https%3A%2F%2Fyt.com%2Fv%3Dtest") { headers.append("X-Instance-Token", token) }.status)
    }
}
