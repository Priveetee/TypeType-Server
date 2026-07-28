package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamRoutesDeliveryModeTest {
    private val sabrService: StreamService = mockk()
    private val nicoNicoService: StreamService = mockk()
    private val bilibiliService: StreamService = mockk()

    @Test
    fun `removed classic endpoints are not registered`() = testApplication {
        application { installRoutes() }

        assertEquals(HttpStatusCode.NotFound, client.get("/streams?url=$VIDEO_URL").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/streams/legacy?url=$VIDEO_URL").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/streams/youtube/legacy?url=$VIDEO_URL").status)
    }

    @Test
    fun `sabr endpoint returns only sabr streams`() = testApplication {
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(mixedResponse())
        var sabrFilterCalled = false
        application {
            installRoutes { _, data ->
                sabrFilterCalled = true
                data
            }
        }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"deliveryMethod\":\"sabr\""))
        assertFalse(body.contains("\"itag\":18"))
        assertTrue(sabrFilterCalled)
        coVerify(exactly = 1) { sabrService.getStreamInfo(VIDEO_URL) }
    }

    @Test
    fun `sabr endpoint rejects direct only extraction`() = testApplication {
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(
                videoOnlyStreams = listOf(testVideoStream()),
                audioStreams = listOf(testAudioStream()),
            ),
        )
        application { installRoutes() }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"no_playable_streams\""))
    }

    @Test
    fun `sabr endpoint removes direct live manifest`() = testApplication {
        val live = sabrResponse().copy(
            hlsUrl = "/streams/hls-manifest?url=live",
            isLive = true,
            isLiveContent = true,
            hasLiveManifest = true,
        )
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(live)
        application { installRoutes() }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"hlsUrl\":\"\""))
        assertFalse(response.bodyAsText().contains("hls-manifest"))
    }

    @Test
    fun `provider endpoints use isolated services`() = testApplication {
        coEvery { nicoNicoService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        coEvery { bilibiliService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        application { installRoutes() }

        val nicoResponse = client.get("/streams/niconico?url=$NICONICO_URL")
        val bilibiliResponse = client.get("/streams/bilibili?url=$BILIBILI_URL")

        assertEquals(HttpStatusCode.OK, nicoResponse.status)
        assertEquals(HttpStatusCode.OK, bilibiliResponse.status)
        coVerify(exactly = 1) { nicoNicoService.getStreamInfo(NICONICO_URL) }
        coVerify(exactly = 1) { bilibiliService.getStreamInfo(BILIBILI_URL) }
        coVerify(exactly = 0) { sabrService.getStreamInfo(any()) }
    }

    @Test
    fun `provider endpoints reject mismatched urls`() = testApplication {
        application { installRoutes() }

        val response = client.get("/streams/niconico?url=$BILIBILI_URL")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"provider_mismatch\""))
        coVerify(exactly = 0) { nicoNicoService.getStreamInfo(any()) }
        coVerify(exactly = 0) { bilibiliService.getStreamInfo(any()) }
    }

    private fun Application.installRoutes(
        sabrFilter: suspend (String, StreamResponse) -> StreamResponse = { _, data -> data },
    ) {
        install(ContentNegotiation) { json() }
        routing {
            streamRoutes(
                streamService = sabrService,
                nicoNicoStreamService = nicoNicoService,
                bilibiliStreamService = bilibiliService,
                sabrStreamContractFilter = sabrFilter,
            )
        }
    }

    private fun mixedResponse(): StreamResponse = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream(itag = 18), sabrVideo()),
        audioStreams = listOf(testAudioStream(), testAudioStream(itag = 140, deliveryMethod = "sabr")),
    )

    private fun sabrResponse(): StreamResponse = testStreamResponse(
        videoOnlyStreams = listOf(sabrVideo()),
        audioStreams = listOf(testAudioStream(itag = 140, deliveryMethod = "sabr")),
    )

    private fun sabrVideo() = testVideoStream(itag = 137).copy(
        url = "",
        deliveryMethod = "sabr",
        manifestUrl = "/sabr/manifest/test",
    )

    private companion object {
        const val VIDEO_URL = "https://youtube.com/watch?v=test"
        const val NICONICO_URL = "https://www.nicovideo.jp/watch/sm9"
        const val BILIBILI_URL = "https://www.bilibili.com/video/BV1test"
    }
}
