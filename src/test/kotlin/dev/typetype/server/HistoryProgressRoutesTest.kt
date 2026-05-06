package dev.typetype.server

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.routes.progressRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import dev.typetype.server.services.ProgressService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HistoryProgressRoutesTest {
    private val historyService = HistoryService()
    private val progressService = ProgressService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        private const val VIDEO_URL = "https://yt.test/watch?v=history-progress"
        private const val ENCODED_VIDEO_URL = "https%3A%2F%2Fyt.test%2Fwatch%3Fv%3Dhistory-progress"

        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit): Unit = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                historyRoutes(historyService, auth)
                progressRoutes(progressService, auth)
            }
        }
        block()
    }

    @Test
    fun `GET history returns saved progress converted to seconds`(): Unit = withApp {
        historyService.add(TEST_USER_ID, history(progress = 0L))
        progressService.upsert(TEST_USER_ID, VIDEO_URL, 12_345L)
        val progress = client.get("/progress?url=$ENCODED_VIDEO_URL") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val history = client.get("/history") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        val historyItems = Json.decodeFromString<List<HistoryItem>>(history.bodyAsText())
        assertEquals(HttpStatusCode.OK, progress.status)
        assertEquals(true, progress.bodyAsText().contains("\"position\":12345"))
        assertEquals(12L, historyItems.single().progress)
    }

    @Test
    fun `POST history keeps newer saved progress instead of zero`(): Unit = withApp {
        progressService.upsert(TEST_USER_ID, VIDEO_URL, 45_000L)
        val response = client.post("/history") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(historyBody(progress = 0L))
        }
        val created = Json.decodeFromString<HistoryItem>(response.bodyAsText())
        val listed = Json.decodeFromString<List<HistoryItem>>(client.get("/history") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText())
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(45L, created.progress)
        assertEquals(45L, listed.single().progress)
    }

    private fun history(progress: Long): HistoryItem = HistoryItem(url = VIDEO_URL, title = "Video", thumbnail = "", channelName = "Channel", channelUrl = "", duration = 120L, progress = progress)

    private fun historyBody(progress: Long): String = """{"url":"$VIDEO_URL","title":"Video","thumbnail":"","channelName":"Channel","channelUrl":"","duration":120,"progress":$progress}"""
}
