package dev.typetype.server

import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SettingsService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WatchHistoryTrackingRoutesTest {
    private val history = HistoryService()
    private val progress = ProgressService()
    private val settings = SettingsService()
    private val auth = AuthService.fixed(TEST_USER_ID)

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
            routing {
                historyRoutes(history, auth, settings)
                progressRoutes(progress, auth, settings)
            }
        }
        block()
    }

    @Test
    fun `POST history does not persist when watch history is disabled`() = withApp {
        settings.upsert(TEST_USER_ID, SettingsItem(disableWatchHistory = true))

        val response = client.post("/history") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"url":"https://yt.com","title":"Test","thumbnail":"","channelName":"Ch","channelUrl":"","duration":100,"progress":0}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val historyResponse = client.get("/history") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals("0", historyResponse.headers["X-Total-Count"])
        assertEquals("[]", historyResponse.bodyAsText())
    }

    @Test
    fun `path PUT progress does not persist when watch history is disabled`() = withApp {
        settings.upsert(TEST_USER_ID, SettingsItem(disableWatchHistory = true))

        val response = client.put("/progress/https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":10000}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"position\":10000"))
        assertNull(progress.get(TEST_USER_ID, "https://yt.com/v?v=test"))
    }

    @Test
    fun `query PUT progress does not persist when watch history is disabled`() = withApp {
        settings.upsert(TEST_USER_ID, SettingsItem(disableWatchHistory = true))

        val response = client.put("/progress?url=https%3A%2F%2Fyt.com%2Fv%3Fv%3Dtest") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"position":3300}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"position\":3300"))
        assertNull(progress.get(TEST_USER_ID, "https://yt.com/v?v=test"))
    }
}
