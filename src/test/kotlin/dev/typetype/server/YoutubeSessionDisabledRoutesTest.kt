package dev.typetype.server

import dev.typetype.server.routes.youtubeSessionRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeSessionService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeSessionDisabledRoutesTest {
    private val auth = AuthService.fixed(TEST_USER_ID)
    private val service = YoutubeSessionService(null)

    @Test
    fun `pairing returns unavailable when encryption key is absent`() = withApp {
        val response = client.post("/youtube-session/pairing") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"youtube_session_unavailable\""))
    }

    @Test
    fun `status stays disconnected when encryption key is absent`() = withApp {
        val response = client.get("/youtube-session/status") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"disconnected\""))
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeSessionRoutes(service, auth) }
        }
        block()
    }
}
