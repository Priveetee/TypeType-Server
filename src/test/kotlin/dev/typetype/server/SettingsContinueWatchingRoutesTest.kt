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

class SettingsContinueWatchingRoutesTest {
    private val service = SettingsService()
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
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, auth) }
        }
        block()
    }

    @Test
    fun `GET settings includes hide continue watching default false`() = withApp {
        val body = getSettings()

        assertTrue(body.contains("\"hideContinueWatching\":false"))
    }

    @Test
    fun `PUT settings persists hide continue watching true then false`() = withApp {
        val enabled = putSettings(hideContinueWatching = true)
        assertEquals(HttpStatusCode.OK, enabled.status)
        assertTrue(enabled.bodyAsText().contains("\"hideContinueWatching\":true"))
        assertTrue(getSettings().contains("\"hideContinueWatching\":true"))

        val disabled = putSettings(hideContinueWatching = false)
        assertEquals(HttpStatusCode.OK, disabled.status)
        assertTrue(disabled.bodyAsText().contains("\"hideContinueWatching\":false"))
        assertTrue(getSettings().contains("\"hideContinueWatching\":false"))
    }

    private suspend fun ApplicationTestBuilder.getSettings(): String =
        client.get("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }.bodyAsText()

    private suspend fun ApplicationTestBuilder.putSettings(hideContinueWatching: Boolean) =
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody(hideContinueWatching))
        }

    private fun settingsBody(hideContinueWatching: Boolean): String = """
        {"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false,"hideContinueWatching":$hideContinueWatching}
    """.trimIndent()
}
