package dev.typetype.server

import dev.typetype.server.services.AuthCookieHelpers
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthCookieHelpersTest {
    @Test
    fun `extract refresh token from cookie header`() = testApplication {
        application {
            routing {
                get("/probe") {
                    call.respondText(AuthCookieHelpers.extractRefreshToken(call) ?: "none")
                }
            }
        }
        val response = client.get("/probe") {
            header(HttpHeaders.Cookie, "foo=bar; refresh_token=abc123; x=y")
        }
        assertEquals("abc123", response.bodyAsText())
    }
}
