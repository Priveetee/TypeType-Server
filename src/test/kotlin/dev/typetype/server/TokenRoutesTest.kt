package dev.typetype.server

import dev.typetype.server.routes.tokenRoutes
import dev.typetype.server.services.TokenService
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenRoutesTest {

    private val tokenService: TokenService = mockk()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { tokenRoutes(tokenService) }
        }
        block()
    }

    @Test
    fun `GET token returns 200`() = withApp {
        every { tokenService.getOrGenerate() } returns "test-token"
        val response = client.get("/token")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
