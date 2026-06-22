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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsAccessModeRoutesTest {
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
        application { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }; routing { settingsRoutes(service, auth) } }
        block()
    }

    @Test
    fun `GET settings returns unrestricted access mode by default`() = withApp {
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"unrestricted\""))
    }

    @Test
    fun `PUT settings persists allow list access mode`() = withApp {
        putSettings("allow_list")
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"allow_list\""))
    }

    @Test
    fun `PUT settings normalizes unknown access mode to unrestricted`() = withApp {
        putSettings("bad")
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"accessMode\":\"unrestricted\""))
    }

    private suspend fun ApplicationTestBuilder.putSettings(accessMode: String) {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false,"accessMode":"$accessMode"}""")
        }
    }
}
