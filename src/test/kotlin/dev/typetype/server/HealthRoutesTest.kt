package dev.typetype.server

import dev.typetype.server.routes.publicMetadataRoutes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HealthRoutesTest {

    @Test
    fun `health returns ok without calling instance provider`() = testApplication {
        var called = false
        application {
            install(ContentNegotiation) { json() }
            routing {
                publicMetadataRoutes {
                    called = true
                    error("instance provider should not be called")
                }
            }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
        assertFalse(called)
    }
}
