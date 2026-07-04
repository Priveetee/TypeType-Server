package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
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

        assertSame(segment, fetched)
        assertEquals(startMs + durationMs, holder.readerHeadMs())
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.lastModified } returns 1L
        every { format.xtags } returns null
        return format
    }

    private fun mediaSegment(itag: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.isInitSegment } returns false
        every { header.itag } returns itag
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        every { header.sequenceNumber } returns 1
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
