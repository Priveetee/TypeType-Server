package dev.typetype.server.routes

import dev.typetype.server.services.CachedSabrSegment
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrPlaybackWindowBuilderTest {
    @Test
    fun `window rebases stale predicted sequence from media header timing`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 299, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 491_203L) } returns 98
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 488_200L) } returns 49
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.format.itag == 299 && it.sequenceNumber == 101 }
                ?.let { mediaSegment(sequence = 101, startMs = 488_200L, durationMs = 6_500L) }
        }
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 299 && request.sequenceNumber == 98 -> cached(299, 98, 474_000L, 4_000L)
                request.format.itag == 299 && request.sequenceNumber == 101 -> cached(299, 101, 488_200L, 6_500L)
                request.format.itag == 140 && request.sequenceNumber == 49 -> cached(140, 49, 479_259L, 9_985L)
                request.format.itag == 140 && request.sequenceNumber == 50 -> cached(140, 50, 489_244L, 9_985L)
                else -> null
            }
        }

        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 491_203L, 299, 140, bufferGoalMs = 1_000L),
        )

        assertTrue(result.isReady)
        val videoSegment = result.response.video.segments.single()
        assertEquals("/api/sabr/playback/session/299/segment/101?generation=0", videoSegment.url)
        assertEquals(488_200L, videoSegment.startMs)
        verify(exactly = 1) { streamState.jumpBufferedTo(video, 101) }
    }

    @Test
    fun `window waits for enough media to outlast manifest refresh`() = runTest {
        val audio = format(itag = 140, isAudio = true)
        val video = format(itag = 401, isAudio = false)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns streamState
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 300_000L) } returns 60
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 296_600L) } returns 30
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        var completeWindow = false
        coEvery { store.cachedSegment(holder, any()) } answers {
            val request = secondArg<SabrSegmentRequest>()
            when {
                request.format.itag == 401 && request.sequenceNumber == 60 -> cached(401, 60, 296_600L, 6_900L)
                request.format.itag == 401 && request.sequenceNumber == 61 && completeWindow ->
                    cached(401, 61, 303_500L, 6_100L)
                request.format.itag == 401 && request.sequenceNumber == 62 && completeWindow ->
                    cached(401, 62, 309_600L, 3_134L)
                request.format.itag == 140 && request.sequenceNumber == 30 -> cached(140, 30, 289_552L, 9_985L)
                request.format.itag == 140 && request.sequenceNumber == 31 -> cached(140, 31, 299_537L, 9_985L)
                request.format.itag == 140 && request.sequenceNumber == 32 && completeWindow ->
                    cached(140, 32, 309_522L, 9_985L)
                else -> null
            }
        }
        val request = SabrPlaybackWindowRequest(0L, 300_000L, 401, 140, bufferGoalMs = 30_000L)
        val builder = SabrPlaybackWindowBuilder(store)

        assertFalse(builder.build(holder, request).isReady)
        completeWindow = true
        assertTrue(builder.build(holder, request).isReady)
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session = session,
        info = mockk<YoutubeSabrInfo>(),
        audioFormat = audio,
        videoFormat = video,
        sessionToken = "session",
        key = SabrSessionKey("video", "user", 140, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.approxDurationMs } returns 900_000L
        every { format.initializationUrl } returns null
        return format
    }

    private fun mediaSegment(sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }

    private fun cached(itag: Int, sequence: Int, startMs: Long, durationMs: Long): CachedSabrSegment = CachedSabrSegment(
        itag = itag,
        sequence = sequence,
        init = false,
        startMs = startMs,
        durationMs = durationMs,
        mimeType = if (itag == 140) "audio/mp4" else "video/mp4",
        bytesBase64 = "AA==",
        byteLength = 1,
    )
}
