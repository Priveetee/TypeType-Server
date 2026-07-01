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
import java.time.Instant

class SabrSessionPumpTest {
    @Test
    fun `fetchSegment fetches requested later media segments directly`() = runTest {
        assertLaterSegmentFetched(itag = 137, sequence = 14, startMs = 60033L, durationMs = 4366L)
        assertLaterSegmentFetched(itag = 140, sequence = 8, startMs = 69892L, durationMs = 9985L)
    }

    private suspend fun assertLaterSegmentFetched(itag: Int, sequence: Int, startMs: Long, durationMs: Long) {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        val request = SabrSegmentRequest.media(format, sequence)
        val session = mockk<YoutubeSabrSession>()
        val segment = mediaSegment(startMs, durationMs)
        every { session.getCachedSegment(request) } returns null
        every { session.isBeyondEnd(request) } returns false
        every { session.prepareForRewind(request) } returns Unit
        every { session.prepareForForwardJump(request) } returns Unit
        every { session.fetchSegment(request, any<Localization>()) } returns segment
        every { session.setPlayHeadMs(startMs + durationMs) } returns Unit
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = format,
            videoFormat = format,
            sessionToken = "session-token",
            lastRequestAt = Instant.EPOCH,
        )

        val fetched = SabrSessionPump().fetchSegment(holder, request)

        assertSame(segment, fetched)
        verify { session.prepareForRewind(request) }
        verify { session.prepareForForwardJump(request) }
        verify { session.fetchSegment(request, any<Localization>()) }
        verify { session.setPlayHeadMs(startMs + durationMs) }
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
