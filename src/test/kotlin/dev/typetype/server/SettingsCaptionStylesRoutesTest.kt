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

class SettingsCaptionStylesRoutesTest {
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
    fun `GET settings returns default caption styles`() = withApp {
        val response = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"captionStyles\""))
        assertTrue(body.contains("\"fontFamily\":\"\""))
        assertTrue(body.contains("\"displayBgOpacity\":\"\""))
    }

    @Test
    fun `GET settings returns persisted caption styles after PUT`() = withApp {
        client.put("/settings") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(captionStylesBody)
        }
        val body = client.get("/settings") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(body.contains("\"fontFamily\":\"monospace\""))
        assertTrue(body.contains("\"fontSize\":\"150%\""))
        assertTrue(body.contains("\"textShadow\":\"outline\""))
    }

    private val captionStylesBody = """
        {"defaultService":0,"defaultQuality":"720p","autoplay":false,"volume":0.5,"muted":true,"captionStyles":{"fontFamily":"monospace","fontSize":"150%","textColor":"#ffffff","textOpacity":"100%","textShadow":"outline","textBg":"#000000","textBgOpacity":"70%","displayBg":"#000000","displayBgOpacity":"0%"}}
    """.trimIndent()
}
