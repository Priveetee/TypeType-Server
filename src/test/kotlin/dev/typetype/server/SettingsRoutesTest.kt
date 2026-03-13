package dev.typetype.server

import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.TokenService
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
import kotlinx.serialization.json.Json
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRoutesTest {

    private val service = SettingsService()
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
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
            routing { settingsRoutes(service, TokenService.fixed(token)) }
        }
        block()
    }

    private val settingsBody = """{"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false}"""

    @Test
    fun `GET settings without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/settings").status)
    }

    @Test
    fun `GET settings returns 200 with defaults when no row exists`() = withApp {
        val response = client.get("/settings") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"volume\":1.0"))
        assertTrue(body.contains("\"muted\":false"))
    }

    @Test
    fun `PUT settings returns 200 and persists values`() = withApp {
        val response = client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"volume\":1.0"))
        assertTrue(body.contains("\"muted\":false"))
    }

    @Test
    fun `GET settings returns persisted values after PUT`() = withApp {
        client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultService":0,"defaultQuality":"720p","autoplay":false,"volume":0.5,"muted":true}""")
        }
        val body = client.get("/settings") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"volume\":0.5"))
        assertTrue(body.contains("\"muted\":true"))
        assertTrue(body.contains("\"defaultQuality\":\"720p\""))
    }

    @Test
    fun `GET settings returns defaults for new fields when no row exists`() = withApp {
        val body = client.get("/settings") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"subtitlesEnabled\":false"))
        assertTrue(body.contains("\"defaultSubtitleLanguage\":\"\""))
        assertTrue(body.contains("\"defaultAudioLanguage\":\"\""))
        assertTrue(!body.contains("subscriptionSyncInterval"))
    }

    @Test
    fun `PUT settings persists new fields and GET returns them`() = withApp {
        client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"defaultService":0,"defaultQuality":"1080p","autoplay":true,"volume":1.0,"muted":false,"subtitlesEnabled":true,"defaultSubtitleLanguage":"fr","defaultAudioLanguage":"fr","subscriptionSyncInterval":60}""")
        }
        val body = client.get("/settings") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"subtitlesEnabled\":true"))
        assertTrue(body.contains("\"defaultSubtitleLanguage\":\"fr\""))
        assertTrue(body.contains("\"defaultAudioLanguage\":\"fr\""))
        assertTrue(!body.contains("subscriptionSyncInterval"))
    }

    @Test
    fun `PUT settings with invalid body returns 400`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""not json""")
        }.status)
    }
}
