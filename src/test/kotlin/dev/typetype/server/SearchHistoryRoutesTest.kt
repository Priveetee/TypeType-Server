package dev.typetype.server

import dev.typetype.server.models.SearchHistoryItem
import dev.typetype.server.routes.searchHistoryRoutes
import dev.typetype.server.services.SearchHistoryService
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
import io.mockk.coJustRun
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchHistoryRoutesTest {

    private val service: SearchHistoryService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { searchHistoryRoutes(service, token) }
        }
        block()
    }

    @Test
    fun `GET search-history without token returns 401`() = withApp {
        val response = client.get("/search-history")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET search-history returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/search-history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST search-history returns 201`() = withApp {
        coEvery { service.add(any()) } returns SearchHistoryItem(term = "rick")
        val response = client.post("/search-history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"term":"rick"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST search-history with invalid body returns 400`() = withApp {
        val response = client.post("/search-history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE search-history returns 204`() = withApp {
        coJustRun { service.deleteAll() }
        val response = client.delete("/search-history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
