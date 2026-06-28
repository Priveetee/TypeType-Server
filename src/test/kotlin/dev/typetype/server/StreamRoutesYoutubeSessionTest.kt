package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_RECONNECT_ERROR
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

class StreamRoutesYoutubeSessionTest {
    private val streamService: StreamService = mockk()

    @Test
    fun `GET streams uses authenticated YouTube session when bearer is valid`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Failure("public path")
        application { testRoutes { userId, _ -> if (userId == TEST_USER_ID) ExtractionResult.Success(testStreamResponse()) else null } }
        val response = client.get("/streams?url=https://youtube.com/watch?v=test") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        coVerify(exactly = 0) { streamService.getStreamInfo(any()) }
    }

    @Test
    fun `GET streams merges authenticated HLS with public direct streams`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(videoOnlyStreams = listOf(testVideoStream()), audioStreams = listOf(testAudioStream()))
        )
        application {
            testRoutes { _, _ ->
                ExtractionResult.Success(
                    testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
                        .copy(hlsUrl = "/streams/hls-manifest?token=signed-hls")
                )
            }
        }
        val response = client.get("/streams?url=https://youtube.com/watch?v=test") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val body = response.bodyAsText()
        assertTrue(body.contains("\"hlsUrl\":\"/streams/hls-manifest?token=signed-hls\""))
        assertTrue(response.headers.getAll(HttpHeaders.SetCookie).orEmpty().any {
            it.startsWith("${SignedHlsManifestCookie.name("https://youtube.com/watch?v=test")}=signed-hls")
        })
        assertTrue(body.contains("\"videoOnlyStreams\":["))
        assertTrue(body.contains("\"audioStreams\":["))
        coVerify(exactly = 1) { streamService.getStreamInfo(any()) }
    }

    @Test
    fun `GET streams ignores authenticated path without valid bearer`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        application { testRoutes { _, _ -> ExtractionResult.Failure("unexpected session path") } }
        val response = client.get("/streams?url=https://youtube.com/watch?v=test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("public, max-age=21600, stale-while-revalidate=3600", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `GET streams returns stable code when YouTube session needs reconnect`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Failure("public path")
        application { testRoutes { _, _ -> ExtractionResult.BadRequest(YOUTUBE_SESSION_RECONNECT_ERROR) } }
        val response = client.get("/streams?url=https://youtube.com/watch?v=test") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"youtube_session_needs_reconnect\""))
    }

    private fun io.ktor.server.application.Application.testRoutes(
        block: suspend (String, String) -> ExtractionResult<dev.typetype.server.models.StreamResponse>?,
    ): Unit {
        install(ContentNegotiation) { json() }
        routing { streamRoutes(streamService, AuthService.fixed(TEST_USER_ID), block) }
    }
}
