package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.schabi.newpipe.extractor.localization.Localization
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
    fun `fetchSegment fetches requested later media segments directly`() = runTest {
        assertLaterSegmentFetched(itag = 137, sequence = 14, startMs = 60033L, durationMs = 4366L)
        assertLaterSegmentFetched(itag = 140, sequence = 8, startMs = 69892L, durationMs = 9985L)
    }

    private suspend fun assertLaterSegmentFetched(itag: Int, sequence: Int, startMs: Long, durationMs: Long) {
        val audio = sabrFormat(140, isAudio = true)
        val video = sabrFormat(137, isAudio = false)
        val format = if (itag == 140) audio else video
        val companion = if (itag == 140) video else audio
        val request = SabrSegmentRequest.media(format, sequence)
        val session = mockk<YoutubeSabrSession>()
        val streamState = mockk<YoutubeSabrStreamState>()
        val segment = mediaSegment(startMs, durationMs)
        every { session.getCachedSegment(any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { session.prepareForRewind(request) } returns Unit
        every { session.prepareForForwardJump(request) } returns Unit
        every { session.streamState } returns streamState
        every { streamState.getSegmentStartMs(format, sequence) } returns startMs
        every { streamState.setPlayerTimeMs(startMs + 1L) } returns Unit
        every { streamState.setRequestTrackMode(any(), any(), any()) } returns Unit
        every { streamState.setFullyBuffered(companion, true) } returns Unit
        every { streamState.setFullyBuffered(format, false) } returns Unit
        every { session.fetchSegment(any(), any<Localization>()) } returns segment
        every { session.setPlayHeadMs(startMs + durationMs) } returns Unit
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            lastRequestAt = Instant.EPOCH,
        )

        val fetched = SabrSessionPump().fetchSegment(holder, request)

        assertSame(segment, fetched)
        verify { session.prepareForRewind(request) }
        verify { session.prepareForForwardJump(request) }
        verify { streamState.setPlayerTimeMs(startMs + 1L) }
        verifyTrackMode(streamState, isAudio = itag == 140)
        verify { streamState.setFullyBuffered(companion, true) }
        verify { streamState.setFullyBuffered(format, false) }
        verify { session.fetchSegment(request, any<Localization>()) }
        verify { session.setPlayHeadMs(startMs + durationMs) }
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }

    private fun verifyTrackMode(streamState: YoutubeSabrStreamState, isAudio: Boolean) {
        if (isAudio) {
            verify { streamState.setRequestTrackMode(1, true, false) }
        } else {
            verify { streamState.setRequestTrackMode(2, false, true) }
        }
    }

    private fun mediaSegment(startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.isInitSegment } returns false
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
