package dev.typetype.server

import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
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

class ProgressRoutesTest {

    private val service = ProgressService()
    private val auth = AuthService.fixed(TEST_USER_ID)

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
            routing { progressRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET progress without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest").status)
    }

    @Test
    fun `GET progress returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.status)
    }

    @Test
    fun `PUT progress returns 200 and persists position`() = withApp {
        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":10000}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"position\":10000"))
    }

    @Test
    fun `GET progress returns 200 after PUT`() = withApp {
        service.upsert(TEST_USER_ID, "https://yt.com/v?v=test", 10000L)
        val response = client.get("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"position\":10000"))
    }

    @Test
    fun `PUT progress with invalid body returns 400`() = withApp {
        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT progress keeps previous value when payload resets to zero`() = withApp {
        service.upsert(TEST_USER_ID, "https://yt.com/v?v=test", 12000L)
        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":0}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"position\":12000"))
    }

    @Test
    fun `query progress endpoint supports put and get`() = withApp {
        val putResponse = client.put("/progress?url=https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":3300}""")
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)
        val getResponse = client.get("/progress?url=https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(getResponse.bodyAsText().contains("\"position\":3300"))
    }
}
