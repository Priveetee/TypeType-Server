package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.StreamService
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabrBootstrapStreamRoutesTest {
    @Test
    fun `bootstrap endpoint uses isolated sabr service`() = testApplication {
        val full = mockk<StreamService>()
        val bootstrap = mockk<StreamService>()
        var youtubeSessionCalls = 0
        coEvery { bootstrap.getStreamInfo(VIDEO_URL) } returns ExtractionResult.Success(sabrResponse())
        application {
            install(ContentNegotiation) { json() }
            routing {
                streamRoutes(
                    streamService = full,
                    authService = AuthService.fixed(TEST_USER_ID),
                    youtubeSessionStreamInfo = { _, _ ->
                        youtubeSessionCalls += 1
                        ExtractionResult.Failure("unexpected session extraction")
                    },
                    sabrBootstrapStreamService = bootstrap,
                )
            }
        }

        val response = client.get("/streams/youtube/sabr/bootstrap?url=$VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"deliveryMethod\":\"sabr\""))
        assertEquals(0, youtubeSessionCalls)
        coVerify(exactly = 1) { bootstrap.getStreamInfo(VIDEO_URL) }
        coVerify(exactly = 0) { full.getStreamInfo(any()) }
    }

    private fun sabrResponse() = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream(itag = 137).copy(deliveryMethod = "sabr")),
        audioStreams = listOf(testAudioStream(itag = 140, deliveryMethod = "sabr")),
    )

    private companion object {
        const val VIDEO_URL = "https://youtube.com/watch?v=f6f3PhauXyg"
    }
}
