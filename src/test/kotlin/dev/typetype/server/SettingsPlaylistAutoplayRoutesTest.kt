package dev.typetype.server

import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SettingsService
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsPlaylistAutoplayRoutesTest {
    private val service = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `GET settings returns playlist autoplay screen skip disabled by default`() = withApp {
        val response = client.get("/settings") { auth() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"skipPlaylistAutoplayScreen\":false"))
    }

    @Test
    fun `PUT settings persists playlist autoplay screen skip without resetting existing settings`() = withApp {
        val first = client.put("/settings") {
            authJson()
            setBody("""{"autoplay":false,"defaultQuality":"720p"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        val second = client.put("/settings") {
            authJson()
            setBody("""{"skipPlaylistAutoplayScreen":true}""")
        }
        val body = second.bodyAsText()

        assertEquals(HttpStatusCode.OK, second.status)
        assertContainsAll(body, listOf(
            "\"skipPlaylistAutoplayScreen\":true",
            "\"autoplay\":false",
            "\"defaultQuality\":\"720p\"",
        ))
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(): Unit =
        headers.append(HttpHeaders.Authorization, "Bearer test-jwt")

    private fun io.ktor.client.request.HttpRequestBuilder.authJson(): Unit {
        auth()
        headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    private fun assertContainsAll(body: String, values: List<String>): Unit =
        values.forEach { assertTrue(body.contains(it)) }
}
