package dev.typetype.server

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.services.HistoryService
import io.ktor.client.request.delete
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HistoryRoutesTest {

    private val service = HistoryService()
    private val token = "test-token"

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
            routing { historyRoutes(service, token) }
        }
        block()
    }

    private val historyBody = """{"url":"https://yt.com","title":"Test","thumbnail":"","channelName":"Ch","channelUrl":"","duration":100,"progress":0}"""

    @Test
    fun `GET history without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/history").status)
    }

    @Test
    fun `GET history returns 200 with empty list`() = withApp {
        val response = client.get("/history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST history returns 201 and persists item`() = withApp {
        val response = client.post("/history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(historyBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"url\":\"https://yt.com\""))
    }

    @Test
    fun `GET history returns persisted items`() = withApp {
        service.add(HistoryItem(url = "https://yt.com", title = "Test", thumbnail = "", channelName = "Ch", channelUrl = "", duration = 100L, progress = 0L))
        val body = client.get("/history") { headers.append("X-Instance-Token", token) }.bodyAsText()
        assertTrue(body.contains("\"url\":\"https://yt.com\""))
    }

    @Test
    fun `DELETE history by id returns 204 when found`() = withApp {
        val item = service.add(HistoryItem(url = "https://yt.com", title = "Test", thumbnail = "", channelName = "Ch", channelUrl = "", duration = 100L, progress = 0L))
        assertEquals(HttpStatusCode.NoContent, client.delete("/history/${item.id}") { headers.append("X-Instance-Token", token) }.status)
    }

    @Test
    fun `DELETE history by id returns 404 when not found`() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.delete("/history/nonexistent") { headers.append("X-Instance-Token", token) }.status)
    }

    @Test
    fun `DELETE history returns 204 and clears all`() = withApp {
        service.add(HistoryItem(url = "https://yt.com", title = "Test", thumbnail = "", channelName = "Ch", channelUrl = "", duration = 100L, progress = 0L))
        assertEquals(HttpStatusCode.NoContent, client.delete("/history") { headers.append("X-Instance-Token", token) }.status)
        assertEquals("[]", client.get("/history") { headers.append("X-Instance-Token", token) }.bodyAsText())
    }
}
