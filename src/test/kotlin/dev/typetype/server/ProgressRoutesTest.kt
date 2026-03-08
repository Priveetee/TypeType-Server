package dev.typetype.server

import dev.typetype.server.models.ProgressItem
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
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

class ProgressRoutesTest {

    private val service: ProgressService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { progressRoutes(service, token) }
        }
        block()
    }

    @Test
    fun `GET progress without token returns 401`() = withApp {
        val response = client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET progress returns 200 when found`() = withApp {
        coEvery { service.get(any()) } returns ProgressItem(videoUrl = "url", position = 1000L)
        val response = client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET progress returns 404 when not found`() = withApp {
        coEvery { service.get(any()) } returns null
        val response = client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT progress returns 200`() = withApp {
        coEvery { service.upsert(any(), any()) } returns ProgressItem(videoUrl = "url", position = 10000L)
        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":10000}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PUT progress with invalid body returns 400`() = withApp {
        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
