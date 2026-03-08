package dev.typetype.server

import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.settingsRoutes
import dev.typetype.server.services.SettingsService
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

class SettingsRoutesTest {

    private val service: SettingsService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { settingsRoutes(service, token) }
        }
        block()
    }

    private val settingsBody = """{"defaultService":0,"defaultQuality":"1080p","autoplay":true}"""
    private fun testSettings() = SettingsItem(defaultService = 0, defaultQuality = "1080p", autoplay = true)

    @Test
    fun `GET settings without token returns 401`() = withApp {
        val response = client.get("/settings")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET settings returns 200`() = withApp {
        coEvery { service.get() } returns testSettings()
        val response = client.get("/settings") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PUT settings returns 200`() = withApp {
        coEvery { service.upsert(any()) } returns testSettings()
        val response = client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(settingsBody)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PUT settings with invalid body returns 400`() = withApp {
        val response = client.put("/settings") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""not json""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
