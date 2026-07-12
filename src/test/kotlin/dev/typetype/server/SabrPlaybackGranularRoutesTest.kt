package dev.typetype.server

import dev.typetype.server.routes.SabrPlaybackWindowHandler
import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackGranularRoutesTest {
    @Test
    fun `position updates player time without prefetching`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/position") {
            contentType(ContentType.Application.Json)
            setBody(positionBody(13_000L))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"playerTimeMs\":13000"))
        assertEquals(13_000L, holder.playerTimeMs())
        verify(exactly = 0) { store.startPump(holder) }
        verify(exactly = 0) { store.warmPlaybackAsync(holder) }
    }

    @Test
    fun `position ignores buffered ranges from the previous video format`() = testApplication {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/position") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"generation":0,"playerTimeMs":13000,"videoItag":136,"audioItag":140,"bufferedRanges":[{"itag":298,"startMs":0,"endMs":12000}]}"""
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"playerTimeMs\":13000"))
    }

    @Test
    fun `segments returns playable partial window without prefetching`() = testApplication {
        val store = partialStore()
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/segments") {
            contentType(ContentType.Application.Json)
            setBody(windowBody())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("segment/1"))
        verify(exactly = 0) { store.startPump(holder) }
        verify(exactly = 0) { store.warmPlaybackAsync(holder) }
    }

    @Test
    fun `window returns playable partial window and requests the next missing segment`() = testApplication {
        val store = partialStore()
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/window") {
            contentType(ContentType.Application.Json)
            setBody(windowBody())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("segment/1"))
        verify(exactly = 1) { store.startPump(holder) }
        verify(exactly = 0) { store.warmPlaybackAsync(holder) }
        verify(exactly = 1) { store.requestSegmentDemand(holder, any()) }
    }

    @Test
    fun `window queues missing first video without direct fetch`() = testApplication {
        val store = audioOnlyStore()
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/window") {
            contentType(ContentType.Application.Json)
            setBody(windowBody())
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("video:136:1 pending"))
        verify(atLeast = 1) { store.requestSegmentDemand(holder, any()) }
    }

    @Test
    fun `window queues first blocker without direct fetch after nonzero start`() = testApplication {
        val store = emptyStore()
        val holder = holder()
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/window") {
            contentType(ContentType.Application.Json)
            setBody(windowBody(340_000L))
        }

        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(body.contains("audio:140:1 pending"))
        verify(exactly = 1) { store.requestSegmentDemand(holder, any()) }
        coVerify(exactly = 0) { store.fetchInitializationData(holder, any()) }
    }

    @Test
    fun `terminal non ump response invalidates info and requests fresh session`() = testApplication {
        val store = emptyStore()
        val holder = holder()
        holder.failTerminal("Expected UMP response, got content type: text/plain; charset=UTF-8")
        every { store.lookupByToken("session-token") } returns holder
        installApp(store)

        val response = client.post("/sabr/playback/session-token/window") {
            contentType(ContentType.Application.Json)
            setBody(windowBody())
        }

        assertTrue(response.bodyAsText().contains("\"recoveryAction\":\"retry_fresh_session\""))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    private fun ApplicationTestBuilder.installApp(store: SabrSessionStore): Unit = application {
        install(ContentNegotiation) { json() }
        val handler = SabrPlaybackWindowHandler(store)
        routing {
            post("/sabr/playback/{sessionId}/position") { handler.position(call, call.parameters["sessionId"].orEmpty()) }
            post("/sabr/playback/{sessionId}/segments") { handler.segments(call, call.parameters["sessionId"].orEmpty()) }
            post("/sabr/playback/{sessionId}/window") { handler.post(call, call.parameters["sessionId"].orEmpty()) }
        }
    }

    private fun partialStore(): SabrSessionStore {
        val store = mockk<SabrSessionStore>(relaxed = true)
        coEvery { store.cachedSegment(any(), any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            if (request.sequenceNumber == 1) cachedSegment(request.format.itag) else null
        }
        return store
    }

    private fun audioOnlyStore(): SabrSessionStore {
        val store = mockk<SabrSessionStore>(relaxed = true)
        coEvery { store.cachedSegment(any(), any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            if (request.format.itag == 140 && request.sequenceNumber == 1) cachedSegment(140) else null
        }
        return store
    }

    private fun emptyStore(): SabrSessionStore {
        val store = mockk<SabrSessionStore>(relaxed = true)
        coEvery { store.cachedSegment(any(), any()) } returns null
        return store
    }

    private fun holder(): SabrSessionHolder {
        val audio = format(140, true)
        val video = format(136, false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { state.setActiveTrackTypes(true, true) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 1
        every { state.getEndSegment(any()) } returns 0L
        every { state.getSegmentStartMs(any(), any()) } answers { (secondArg<Int>() - 1) * 10_000L }
        every { state.getSegmentEndMs(any(), any()) } answers { secondArg<Int>() * 10_000L }
        every { state.getMinBufferedEndMs() } returns 10_000L
        every { state.setStickyResolutionOverride(any()) } returns Unit
        every { state.setWriteLastManualSelectedResolution(true) } returns Unit
        every { state.setSelectVideoFormatBeforeAudio(any()) } returns Unit
        every { state.setBufferedRangesOverride(null) } returns Unit
        every { state.setBufferedRangesOverride(any()) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", 140, null, 136, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.audioTrackId } returns null
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        every { format.height } returns if (isAudio) 0 else 720
        every { format.approxDurationMs } returns 900_000L
        every { format.initializationUrl } returns null
        return format
    }

    private fun cachedSegment(itag: Int): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = 1,
        init = false,
        startMs = 0L,
        durationMs = 10_000L,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )

    private fun positionBody(ms: Long): String =
        """{"generation":0,"playerTimeMs":$ms,"videoItag":136,"audioItag":140}"""

    private fun windowBody(playerTimeMs: Long = 0L): String =
        """{"generation":0,"playerTimeMs":$playerTimeMs,"videoItag":136,"audioItag":140,"bufferGoalMs":30000}"""
}
