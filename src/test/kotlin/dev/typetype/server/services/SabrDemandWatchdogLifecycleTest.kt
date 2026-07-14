package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrDemandWatchdogLifecycleTest {
    @Test
    fun `returning demand keeps its original deadline`() = runTest {
        withTracker { holder ->
            val first = SabrSegmentRequest.media(holder.audioFormat, 50)
            val second = SabrSegmentRequest.media(holder.audioFormat, 49)
            every { holder.session.isBeyondEnd(any()) } returns false
            every { holder.session.streamState.getSegmentStartMs(holder.audioFormat, 50) } returns 500_000L
            every { holder.session.streamState.getSegmentStartMs(holder.audioFormat, 49) } returns 400_000L
            holder.requestSegmentDemand(first, registeredAtMs = 0L)
            var expired = false
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ true }, holder)
            }
            runCurrent()

            advanceTimeBy(10_000L)
            holder.requestSegmentDemand(second, registeredAtMs = testScheduler.currentTime)
            runCurrent()
            advanceTimeBy(4_900L)
            holder.clearSegmentDemand(second)
            advanceTimeBy(100L)
            runCurrent()

            assertTrue(job.isCompleted)
            assertTrue(expired)
            assertEquals("SABR demand stalled for 140:50", holder.terminalFailure())
        }
    }

    @Test
    fun `discovered end removes demand without terminal failure`() = runTest {
        withTracker { holder ->
            val request = SabrSegmentRequest.media(holder.audioFormat, 85)
            var beyondEnd = false
            var alive = true
            every { holder.session.isBeyondEnd(request) } answers { beyondEnd }
            holder.requestSegmentDemand(request, registeredAtMs = 0L)
            var expired = true
            val job = launch {
                expired = SabrDemandWatchdog(
                    clock = { testScheduler.currentTime },
                    intervalMs = 100L,
                ).monitor({ alive }, holder)
            }
            runCurrent()

            advanceTimeBy(SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS - 100L)
            beyondEnd = true
            advanceTimeBy(100L)
            runCurrent()
            alive = false
            advanceTimeBy(100L)
            runCurrent()

            assertTrue(job.isCompleted)
            assertFalse(expired)
            assertFalse(holder.playbackState() == SabrPlaybackState.TERMINAL)
            assertEquals(null, holder.pendingSegmentDemandSummary())
        }
    }

    private suspend fun withTracker(block: suspend (SabrSessionHolder) -> Unit) {
        SabrSegmentDemandTracker.clearAll()
        try {
            block(holder())
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audio = format(140, true)
        val video = format(299, false)
        every { session.getCachedSegment(any()) } returns null
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        return format
    }
}
