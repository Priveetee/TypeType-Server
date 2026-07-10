package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

class SabrCachedSegmentLocatorTest {
    @Test
    fun `finds authoritative cached segment when predicted sequence is stale`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 101, startMs = 488_200L, durationMs = 6_500L)
        every { format.itag } returns 299
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 101 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 491_203L,
            predictedSequence = 98,
        )

        assertSame(segment, found)
    }

    @Test
    fun `finds next cached sequence across millisecond timing gap`() {
        val format = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>()
        val segment = segment(sequence = 41, startMs = 202_302L, durationMs = 5_672L)
        every { format.itag } returns 315
        every { session.getCachedSegment(any()) } answers {
            firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 41 }?.let { segment }
        }

        val found = session.findCachedMediaAt(
            format = format,
            targetMs = 202_301L,
            predictedSequence = 40,
        )

        assertSame(segment, found)
    }

    private fun segment(sequence: Int, startMs: Long, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.sequenceNumber } returns sequence
        every { header.isInitSegment } returns false
        every { header.startMs } returns startMs
        every { header.durationMs } returns durationMs
        val segment = mockk<SabrMediaSegment>()
        every { segment.header } returns header
        return segment
    }
}
