package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionPumpTest {
    @Test
    fun `fetchSegment serves cached media and records reader position`() = runTest {
        assertCachedSegmentServed(itag = 137, sequence = 14, startMs = 60033L, durationMs = 4366L)
        assertCachedSegmentServed(itag = 140, sequence = 8, startMs = 69892L, durationMs = 9985L)
    }

    @Test
    fun `fetchSegment retries targeted fetch when first segment does not match request`() = runTest {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val request = SabrSegmentRequest.media(audio, 8)
        val wrong = mediaSegment(137, 120_000L, 5_000L, sequence = 8)
        val expected = mediaSegment(140, 120_000L, 10_000L, sequence = 8)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns streamState
        every { session.getCachedSegment(request) } returns null
        every { session.isBeyondEnd(request) } returns false
        every { streamState.setActiveTrackTypes(true, true) } returns Unit
        every { streamState.getMinBufferedEndMs() } returns 0L
        every { streamState.getSegmentStartMs(audio, 8) } returns 120_000L
        every { streamState.getSegmentStartMs(audio, 9) } returns 130_000L
        every { session.fetchMediaSegmentAt(request, any(), false, true, any()) } returnsMany listOf(wrong, expected)
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L),
            lastRequestAt = Instant.EPOCH,
        )

        val fetched = SabrSessionPump().fetchSegment(holder, request)

        assertSame(expected, fetched)
        verify(exactly = 2) { session.fetchMediaSegmentAt(request, any(), false, true, any()) }
    }

    @Test
    fun `reader head is active track intersection and skips already served segments`() {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val holder = sabrHolder(audio, video)
        val videoSegment = mediaSegment(137, 60_000L, 5_000L, sequence = 12)
        val audioSegment = mediaSegment(140, 59_000L, 10_000L, sequence = 7)

        holder.markServed(videoSegment)
        assertEquals(0L, holder.readerHeadMs())
        holder.markServed(audioSegment)

        assertEquals(65_000L, holder.readerHeadMs())
        assertFalse(holder.shouldSend(videoSegment))
        assertTrue(holder.shouldSend(mediaSegment(137, 65_000L, 5_000L, sequence = 13)))
    }

    @Test
    fun `media requests advance past already served boundary segments`() {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns streamState
        every { streamState.setActiveTrackTypes(true, true) } returns Unit
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 60_862L) } returns 12
        every { streamState.getSegmentNumberAtOrAfterTimeMs(audio, 60_862L) } returns 7
        val holder = sabrHolder(audio, video, session, streamState)
        holder.markServed(mediaSegment(137, 55_789L, 5_072L, sequence = 12))
        holder.markServed(mediaSegment(140, 50_876L, 9_985L, sequence = 7))

        val requests = holder.mediaRequestsAt(60_862L)

        assertEquals(13, requests.first { it.format == video }.sequenceNumber)
        assertEquals(8, requests.first { it.format == audio }.sequenceNumber)
    }

    @Test
    fun `media requests skip tracks already ahead of requested time`() {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 60_862L) } returns 12
        val holder = sabrHolder(audio, video, session, streamState)
        holder.markServed(mediaSegment(137, 55_789L, 5_072L, sequence = 12))
        holder.markServed(mediaSegment(140, 59_907L, 9_985L, sequence = 7))

        assertEquals(emptyList<SabrSegmentRequest>(), holder.mediaRequestsAt(59_908L))
        assertEquals(listOf(13), holder.mediaRequestsAt(60_862L).map { it.sequenceNumber })
    }

    @Test
    fun `media fetch requests exact segments for tracks that are not already ahead`() = runTest {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        val expected = mediaSegment(137, 60_860L, 5_072L, sequence = 13)
        every { session.streamState } returns streamState
        every { streamState.setActiveTrackTypes(true, true) } returns Unit
        every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 60_862L) } returns 12
        val holder = sabrHolder(audio, video, session, streamState)
        holder.markServed(mediaSegment(137, 55_789L, 5_072L, sequence = 12))
        holder.markServed(mediaSegment(140, 59_907L, 9_985L, sequence = 7))

        val fetched = SabrSessionMediaFetcher.fetch(holder, 60_862L) { request ->
            if (request.format == video && request.sequenceNumber == 13) expected else null
        }

        assertEquals(listOf(expected), fetched)
    }

    private suspend fun assertCachedSegmentServed(itag: Int, sequence: Int, startMs: Long, durationMs: Long) {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val format = if (itag == 140) audio else video
        val request = SabrSegmentRequest.media(format, sequence)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        val segment = mediaSegment(itag, startMs, durationMs)
        every { session.getCachedSegment(request) } returns segment
        every { session.streamState } returns streamState
        every { streamState.setActiveTrackTypes(true, true) } returns Unit
        val holder = sabrHolder(audio, video, session)

        val fetched = SabrSessionPump().fetchSegment(holder, request)

        assertSame(segment, fetched)
        assertEquals(0L, holder.readerHeadMs())
    }

    private fun sabrHolder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        session: YoutubeSabrSession = mockk<YoutubeSabrSession>(),
        streamState: YoutubeSabrStreamState = mockk<YoutubeSabrStreamState>(),
    ): SabrSessionHolder {
        every { session.streamState } returns streamState
        every { streamState.setActiveTrackTypes(true, true) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", 140, null, 137, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.lastModified } returns 1L
        every { format.xtags } returns null
        return format
    }

    private fun mediaSegment(itag: Int, startMs: Long, durationMs: Long, sequence: Int = 1): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.isInitSegment } returns false
        every { header.itag } returns itag
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.sequenceNumber } returns sequence
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
