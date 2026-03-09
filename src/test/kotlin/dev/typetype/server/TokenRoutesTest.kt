package dev.typetype.server

import dev.typetype.server.routes.tokenRoutes
import dev.typetype.server.services.TokenService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenRoutesTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { tokenRoutes(TokenService()) }
        }
        block()
    }

    @Test
    fun `GET token returns 200`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/token").status)
    }

    @Test
    fun `GET token returns a non-empty token value`() = withApp {
        val body = client.get("/token").bodyAsText()
        assertTrue(body.contains("\"token\""))
    }
}
