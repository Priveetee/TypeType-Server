package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrTransientDemandFailureTest {
    @Test
    fun `transient timeout preserves demand until retry succeeds`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 39)
            val segment = mockk<SabrMediaSegment>()
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            val result = mockk<YoutubeSabrSession.DemandResponseResult>()
            var cached = false
            var attempts = 0
            every { session.streamState } returns streamState
            every { session.getCachedSegment(request) } answers { segment.takeIf { cached } }
            every { streamState.getSegmentStartMs(audio, 39) } returns 379_414L
            every { streamState.getMinBufferedEndMs() } returns 379_233L
            every { result.segmentCount } returns 1
            every { result.targetTrackSegmentCount } returns 1
            every { session.pumpOnceStreamingForDemand(any(), request) } answers {
                attempts++
                if (attempts == 1) throw IOException("timeout")
                cached = true
                result
            }
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ < 2 }, holder, intervalMs = 0L)

            assertEquals(SabrPlaybackState.STOPPED, holder.playbackState())
            assertNull(holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            assertEquals(1_000L, testScheduler.currentTime)
            verify(exactly = 2) { session.pumpOnceStreamingForDemand(any(), request) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
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
        sessionToken = "session-token",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }
}
