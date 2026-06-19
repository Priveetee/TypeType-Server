package dev.typetype.server

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.delete
import io.ktor.client.request.headers
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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val VIDEO_URL = "https://yt.com/watch?v=one"
private const val OTHER_VIDEO_URL = "https://yt.com/watch?v=two"
private const val ORPHAN_VIDEO_URL = "https://yt.com/watch?v=orphan"

class HistoryProgressCleanupRoutesTest {

    private val historyService = HistoryService()
    private val progressService = ProgressService()
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
            routing { historyRoutes(historyService, auth) }
        }
        block()
    }

    @Test
    fun `DELETE history by id clears saved progress for video`() = withApp {
        val item = historyService.add(TEST_USER_ID, history(VIDEO_URL))
        progressService.upsert(TEST_USER_ID, VIDEO_URL, 42_000L)

        val response = client.delete("/history/${item.id}") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNull(progressService.get(TEST_USER_ID, VIDEO_URL))
    }

    @Test
    fun `DELETE history clears saved progress for all history videos`() = withApp {
        historyService.add(TEST_USER_ID, history(VIDEO_URL))
        historyService.add(TEST_USER_ID, history(OTHER_VIDEO_URL))
        progressService.upsert(TEST_USER_ID, VIDEO_URL, 42_000L)
        progressService.upsert(TEST_USER_ID, OTHER_VIDEO_URL, 84_000L)
        progressService.upsert(TEST_USER_ID, ORPHAN_VIDEO_URL, 126_000L)

        val response = client.delete("/history") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNull(progressService.get(TEST_USER_ID, VIDEO_URL))
        assertNull(progressService.get(TEST_USER_ID, OTHER_VIDEO_URL))
        assertNull(progressService.get(TEST_USER_ID, ORPHAN_VIDEO_URL))
    }

    private fun history(url: String): HistoryItem = HistoryItem(
        url = url,
        title = "Test",
        thumbnail = "",
        channelName = "Ch",
        channelUrl = "",
        duration = 100L,
        progress = 0L,
    )

}
