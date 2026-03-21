package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreamRoutesTest {

    private val streamService: StreamService = mockk()

    @Test
    fun `GET streams without url returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET streams with valid url returns 200 on Success`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse())
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=21600, stale-while-revalidate=3600", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `GET streams returns 422 on Failure`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Failure("Extraction failed")
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams?url=https://youtube.com/watch?v=bad")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET streams returns 400 on BadRequest`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.BadRequest("Unsupported URL")
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService) }
        }
        val response = client.get("/streams?url=https://unsupported.com/video")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
