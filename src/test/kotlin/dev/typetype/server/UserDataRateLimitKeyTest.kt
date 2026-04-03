package dev.typetype.server

import dev.typetype.server.services.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserDataRateLimitKeyTest {
    @Test
    fun `uses user key when bearer is valid`() = testApplication {
        val auth = AuthService.fixed("user-42")
        application {
            routing {
                get("/key") {
                    val key = userDataRateLimitKey(call, auth)
                    call.response.headers.append("X-Key", key)
                }
            }
        }
        val response = client.get("/key") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals("user:user-42", response.headers["X-Key"])
    }

    @Test
    fun `falls back to ip key when token is invalid`() = testApplication {
        val auth = AuthService.fixed("user-42")
        application {
            routing {
                get("/key") {
                    val key = userDataRateLimitKey(call, auth)
                    call.response.headers.append("X-Key", key)
                }
            }
        }
        val response = client.get("/key") {
            headers.append(HttpHeaders.Authorization, "Bearer invalid")
        }
        val key = response.headers["X-Key"] ?: ""
        assertEquals(true, key.startsWith("ip:"))
    }
}
