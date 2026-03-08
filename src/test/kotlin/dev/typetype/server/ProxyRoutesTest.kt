package dev.typetype.server

import dev.typetype.server.routes.proxyRoutes
import dev.typetype.server.services.ProxyService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyRoutesTest {

    private val proxyService: ProxyService = mockk()

    @Test
    fun `GET proxy without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { proxyRoutes(proxyService) }
        }
        val response = client.get("/proxy")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
