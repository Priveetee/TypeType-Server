package dev.typetype.server

import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.services.SearchHistoryService
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

class SearchHistoryRoutesTest {

    private val service = SearchHistoryService()
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
            routing { searchHistoryRoutes(service, TokenService.fixed(token)) }
        }
        block()
    }

    @Test
    fun `GET search-history without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/search-history").status)
    }

    @Test
    fun `GET search-history returns 200 with empty list`() = withApp {
        val response = client.get("/search-history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST search-history returns 201 and persists item`() = withApp {
        val response = client.post("/search-history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"term":"rick"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"term\":\"rick\""))
    }

    @Test
    fun `GET search-history returns persisted items`() = withApp {
        service.add("rick")
        val body = client.get("/search-history") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"term\":\"rick\""))
    }

    @Test
    fun `POST search-history with invalid body returns 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.post("/search-history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{}""")
        }.status)
    }

    @Test
    fun `DELETE search-history returns 204 and clears all`() = withApp {
        service.add("rick")
        assertEquals(HttpStatusCode.NoContent, client.delete("/search-history") { headers.append("X-Instance-Token", token) }.status)
        assertEquals("[]", client.get("/search-history") { headers.append("X-Instance-Token", token) }.bodyAsText())
    }
}
