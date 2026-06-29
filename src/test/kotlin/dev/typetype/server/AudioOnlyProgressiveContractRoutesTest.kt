package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.audioOnlyContractRoutes
import dev.typetype.server.routes.audioOnlySourceRoutes
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class AudioOnlyProgressiveContractRoutesTest {
    private val streamService: StreamService = mockk()
    private val proxyService: ProxyService = mockk()
    private val tokenService = AudioOnlyMediaTokenService("test-secret")

    @Test
    fun `GET audio-only rejects HLS manifest audio streams`() = testApplication {
        val hlsAudio = testAudioStream(url = HLS_URL, audioLocale = "en")
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(audioStreams = listOf(hlsAudio))
        )
        installContractApp()

        val response = client.get("/streams/audio-only?url=https://youtube.com/watch?v=test")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("No audio-only stream is available"))
    }

    @Test
    fun `GET audio-only source rejects HLS upstream response`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        coEvery { proxyService.pipe(any(), any(), any()) } returns ExtractionResult.Success(
            ProxyResponse(
                status = 206,
                contentType = "application/vnd.apple.mpegurl",
                contentLength = null,
                contentRange = null,
                acceptRanges = null,
                stream = ByteArrayInputStream("#EXTM3U".toByteArray()),
                close = {},
            )
        )
        installSourceApp()
        val token = tokenService.createToken(null, "https://youtube.com/watch?v=test", false, "en")

        val response = client.get("/streams/audio-only/source?token=$token") {
            header(HttpHeaders.Range, "bytes=0-1023")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("Audio-only source did not return progressive audio"))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installContractApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlyContractRoutes(streamService, tokenService) }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installSourceApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing { audioOnlySourceRoutes(streamService, proxyService, tokenService) }
    }

    private companion object {
        const val HLS_URL = "https://manifest.googlevideo.com/api/manifest/hls/test"
    }
}
