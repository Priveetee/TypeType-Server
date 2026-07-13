package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrDemandFailurePumpTest {
    @Test
    fun `three target omissions terminate demand pump`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(audio, 39)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns streamState
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getSegmentStartMs(audio, 39) } returns 379_414L
            every { streamState.getMinBufferedEndMs() } returns 379_233L
            every { streamState.getBufferedEndMs(audio) } returns 379_233L
            every { session.pumpOnceStreamingForDemand(any(), request) } returns result()
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)

            SabrSessionPump().pumpLoop({ true }, holder, intervalMs = 100L)

            assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
            assertEquals("SABR demand stalled for 140:39", holder.terminalFailure())
            assertNull(holder.pendingSegmentDemandSummary())
            verify(exactly = 3) { session.pumpOnceStreamingForDemand(any(), request) }
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

    private fun result(): YoutubeSabrSession.DemandResponseResult {
        val result = mockk<YoutubeSabrSession.DemandResponseResult>()
        every { result.segmentCount } returns 7
        every { result.targetTrackSegmentCount } returns 1
        return result
    }
}
