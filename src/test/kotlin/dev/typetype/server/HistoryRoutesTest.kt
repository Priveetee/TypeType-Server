package dev.typetype.server

import dev.typetype.server.models.HistoryItem
import dev.typetype.server.routes.historyRoutes
import dev.typetype.server.services.HistoryService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryRoutesTest {

    private val service: HistoryService = mockk()
    private val token = "test-token"

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { historyRoutes(service, token) }
        }
        block()
    }

    private val historyBody = """{"url":"https://yt.com","title":"Test","thumbnail":"","channelName":"Ch","channelUrl":"","duration":100,"progress":0}"""

    private fun testHistoryItem() = HistoryItem(
        url = "https://yt.com", title = "Test", thumbnail = "",
        channelName = "Ch", channelUrl = "", duration = 100L, progress = 0L,
    )

    @Test
    fun `GET history without token returns 401`() = withApp {
        val response = client.get("/history")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET history returns 200`() = withApp {
        coEvery { service.getAll() } returns emptyList()
        val response = client.get("/history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST history returns 201`() = withApp {
        coEvery { service.add(any()) } returns testHistoryItem()
        val response = client.post("/history") {
            headers.append("X-Instance-Token", token)
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(historyBody)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `DELETE history by id returns 204 when found`() = withApp {
        coEvery { service.delete("abc") } returns true
        val response = client.delete("/history/abc") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `DELETE history by id returns 404 when not found`() = withApp {
        coEvery { service.delete("abc") } returns false
        val response = client.delete("/history/abc") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE history returns 204`() = withApp {
        coJustRun { service.deleteAll() }
        val response = client.delete("/history") { headers.append("X-Instance-Token", token) }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
