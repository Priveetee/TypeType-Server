package dev.typetype.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorsOriginsTest {
    @Test
    fun `allowed origins trims comma separated config`() {
        assertEquals(
            setOf("https://watch.example", "http://127.0.0.1:8082"),
            allowedOriginsFromEnv(" https://watch.example, http://127.0.0.1:8082 "),
        )
    }

    @Test
    fun `allowed origins rejects empty config`() {
        assertThrows(IllegalStateException::class.java) { allowedOriginsFromEnv(" , ") }
    }

    @Test
    fun `wildcard origin config accepts any request origin`() {
        assertTrue(setOf("*").allowsCorsOrigin("http://192.168.1.50:8082"))
        assertTrue(setOf("*").allowsCorsOrigin("https://watch.example"))
    }

    @Test
    fun `explicit origin config rejects unknown origins`() {
        val origins = setOf("http://localhost:8082")

        assertTrue(origins.allowsCorsOrigin("http://localhost:8082"))
        assertFalse(origins.allowsCorsOrigin("http://127.0.0.1:8082"))
    }

    @Test
    fun `wildcard config echoes request origin for credentials cors`() = testApplication {
        val origin = "http://192.168.1.50:8082"
        application {
            install(CORS) {
                allowOrigins { setOf("*").allowsCorsOrigin(it) }
                allowCredentials = true
            }
            routing {
                get("/health") { call.respondText("ok") }
            }
        }

        val response = client.get("/health") { header(HttpHeaders.Origin, origin) }

        assertEquals(origin, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("true", response.headers[HttpHeaders.AccessControlAllowCredentials])
    }
}
