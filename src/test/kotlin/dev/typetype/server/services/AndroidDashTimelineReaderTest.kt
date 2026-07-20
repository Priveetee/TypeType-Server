package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

class AndroidDashTimelineReaderTest {
    private val format = mockk<YoutubeSabrFormat> {
        every { itag } returns 137
    }

    @Test
    fun `unknown exact index stays preparing`() {
        val state = mockk<YoutubeSabrStreamState> {
            every { hasSegmentIndex(format) } returns false
        }

        assertEquals(AndroidDashTimelineResult.Pending, AndroidDashTimelineReader.read(state, format))
    }

    @Test
    fun `exact index preserves complete segment timing`() {
        val state = indexedState(count = 3, durationMs = 2_000L)

        val ready = AndroidDashTimelineReader.read(state, format) as AndroidDashTimelineResult.Ready

        assertEquals(1, ready.timeline.startNumber)
        assertEquals(3, ready.timeline.segments.size)
        assertEquals(0L, ready.timeline.segments.first().startMs)
        assertEquals(6_000L, ready.timeline.endMs)
    }

    @Test
    fun `timeline beginning after zero is rejected`() {
        val state = indexedState(count = 2, durationMs = 1_000L, offsetMs = 500L)

        assertInstanceOf(
            AndroidDashTimelineResult.Invalid::class.java,
            AndroidDashTimelineReader.read(state, format),
        )
    }

    @Test
    fun `unbounded exact segment count is rejected`() {
        val state = mockk<YoutubeSabrStreamState> {
            every { hasSegmentIndex(format) } returns true
            every { getEndSegment(format) } returns 100_001L
        }

        assertInstanceOf(
            AndroidDashTimelineResult.Invalid::class.java,
            AndroidDashTimelineReader.read(state, format),
        )
    }

    private fun indexedState(count: Int, durationMs: Long, offsetMs: Long = 0L): YoutubeSabrStreamState = mockk {
        every { hasSegmentIndex(format) } returns true
        every { getEndSegment(format) } returns count.toLong()
        every { getSegmentStartMs(format, any()) } answers { offsetMs + (secondArg<Int>() - 1L) * durationMs }
        every { getSegmentEndMs(format, any()) } answers { offsetMs + secondArg<Int>() * durationMs }
    }
}
