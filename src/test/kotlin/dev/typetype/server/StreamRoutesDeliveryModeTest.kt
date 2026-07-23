package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
    private val legacyService: StreamService = mockk()
    private val genericLegacyService: StreamService = mockk()
    private val nicoNicoService: StreamService = mockk()
    private val bilibiliService: StreamService = mockk()

    @Test
    fun `generic streams endpoint preserves classic contract`() = testApplication {
        coEvery { genericLegacyService.getStreamInfo(any()) } returns ExtractionResult.Success(mixedResponse())
        application { installRoutes() }

        val response = client.get("/streams?url=$VIDEO_URL")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"itag\":18"))
        assertFalse(body.contains("\"deliveryMethod\":\"sabr\""))
        coVerify(exactly = 1) { genericLegacyService.getStreamInfo(VIDEO_URL) }
        coVerify(exactly = 0) { sabrService.getStreamInfo(any()) }
    }

    @Test
    fun `generic legacy endpoint remains provider neutral`() = testApplication {
        coEvery { genericLegacyService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        application { installRoutes() }

        val response = client.get("/streams/legacy?url=$NICONICO_URL")

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify(exactly = 1) { genericLegacyService.getStreamInfo(NICONICO_URL) }
    }

    @Test
    fun `legacy endpoint uses isolated service and strips sabr streams`() = testApplication {
        coEvery { legacyService.getStreamInfo(any()) } returns ExtractionResult.Success(mixedResponse())
        var sabrFilterCalled = false
        application {
            installRoutes { _, data ->
                sabrFilterCalled = true
                data
            }
        }

        val response = client.get("/streams/youtube/legacy?url=$VIDEO_URL")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"itag\":18"))
        assertFalse(body.contains("\"deliveryMethod\":\"sabr\""))
        assertFalse(sabrFilterCalled)
        coVerify(exactly = 1) { legacyService.getStreamInfo(VIDEO_URL) }
        coVerify(exactly = 0) { sabrService.getStreamInfo(any()) }
    }

    @Test
    fun `legacy endpoint short circuits extraction on authenticated hls`() = testApplication {
        val hls = testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
            .copy(hlsUrl = "/streams/hls-manifest?token=legacy")
        application { installRoutes(session = { _, _ -> ExtractionResult.Success(hls) }) }

        val response = client.get("/streams/youtube/legacy?url=$VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"hlsUrl\":\"/streams/hls-manifest?token=legacy\""))
        coVerify(exactly = 0) { legacyService.getStreamInfo(any()) }
        coVerify(exactly = 0) { sabrService.getStreamInfo(any()) }
    }

    @Test
    fun `sabr endpoint never uses the connected YouTube session`() = testApplication {
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(sabrResponse())
        var sessionCalls = 0
        application {
            installRoutes(session = { _, _ ->
                sessionCalls += 1
                ExtractionResult.Success(sabrResponse())
            })
        }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, sessionCalls)
        coVerify(exactly = 1) { sabrService.getStreamInfo(VIDEO_URL) }
        coVerify(exactly = 0) { legacyService.getStreamInfo(any()) }
    }

    @Test
    fun `sabr endpoint ignores authenticated hls and returns only sabr streams`() = testApplication {
        val hls = testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
            .copy(hlsUrl = "/streams/hls-manifest?token=classic")
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(mixedResponse())
        application { installRoutes(session = { _, _ -> ExtractionResult.Success(hls) }) }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"deliveryMethod\":\"sabr\""))
        assertFalse(body.contains("\"itag\":18"))
        assertFalse(body.contains("classic"))
        coVerify(exactly = 1) { sabrService.getStreamInfo(VIDEO_URL) }
    }

    @Test
    fun `sabr endpoint removes hls for live streams`() = testApplication {
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
    fun `legacy endpoint rejects sabr only extraction`() = testApplication {
        coEvery { legacyService.getStreamInfo(any()) } returns ExtractionResult.Success(sabrResponse())
        application { installRoutes() }

        val response = client.get("/streams/youtube/legacy?url=$VIDEO_URL")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"no_playable_streams\""))
    }

    @Test
    fun `sabr endpoint rejects classic only extraction`() = testApplication {
        coEvery { sabrService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(videoOnlyStreams = listOf(testVideoStream()), audioStreams = listOf(testAudioStream())),
        )
        application { installRoutes() }

        val response = client.get("/streams/youtube/sabr?url=$VIDEO_URL")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"no_playable_streams\""))
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
        coVerify(exactly = 0) { legacyService.getStreamInfo(any()) }
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
        session: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
        sabrFilter: suspend (String, StreamResponse) -> StreamResponse = { _, data -> data },
    ): Unit {
        install(ContentNegotiation) { json() }
        routing {
            streamRoutes(
                streamService = sabrService,
                authService = AuthService.fixed(TEST_USER_ID),
                youtubeSessionStreamInfo = session,
                legacyStreamService = legacyService,
                genericLegacyStreamService = genericLegacyService,
                nicoNicoStreamService = nicoNicoService,
                bilibiliStreamService = bilibiliService,
                sabrStreamContractFilter = sabrFilter,
            )
        }
    }

    private fun mixedResponse(): StreamResponse = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream(itag = 18), sabrVideo()),
        audioStreams = listOf(testAudioStream(itag = 140, deliveryMethod = "sabr")),
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
