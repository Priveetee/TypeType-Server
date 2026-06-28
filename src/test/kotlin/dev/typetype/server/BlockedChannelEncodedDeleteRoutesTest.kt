package dev.typetype.server

import dev.typetype.server.routes.blockedRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.BlockedService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BlockedChannelEncodedDeleteRoutesTest {
    private val service = BlockedService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() { TestDatabase.setup() }
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `DELETE blocked channel handles encoded url with query`() = testApplication {
        val url = "https://www.youtube.com/channel/UC123?view=videos"
        service.addChannel(TEST_USER_ID, url)
        application { install(ContentNegotiation) { json() }; routing { blockedRoutes(service, auth) } }

        val response = client.delete("/blocked/channels/https%3A%2F%2Fwww.youtube.com%2Fchannel%2FUC123%3Fview%3Dvideos") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val remaining = client.get("/blocked/channels") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals("[]", remaining.bodyAsText())
    }
}
