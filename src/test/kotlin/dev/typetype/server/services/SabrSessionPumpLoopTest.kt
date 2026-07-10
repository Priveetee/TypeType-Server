package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrSessionPumpLoopTest {
    @Test
    fun `non target media response keeps demand loop paced`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(299, isAudio = false)
            val request = SabrSegmentRequest.media(video, 98)
            val session = mockk<YoutubeSabrSession>()
            val streamState = mockk<YoutubeSabrStreamState>()
            every { session.streamState } returns streamState
            every { streamState.setActiveTrackTypes(true, true) } returns Unit
            every { session.setPlayHeadMs(any()) } returns Unit
            every { session.evictPlayed() } returns Unit
            every { session.requestNumber } returns 12
            every { session.cachedBytes } returns 6_607_100L
            every { session.diagnosticTrace } returns ""
            val rebased = mediaSegment(sequence = 101, startMs = 488_200L, durationMs = 6_500L)
            every { session.getCachedSegment(any()) } answers {
                firstArg<SabrSegmentRequest>().takeIf { it.sequenceNumber == 101 }?.let { rebased }
            }
            every { streamState.getMinBufferedEndMs() } returns 487_134L
            every { streamState.getSegmentStartMs(video, 98) } returns 487_134L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.jumpBufferedTo(video, 101) } returns Unit
            every { session.pumpOnceStreamingUntilCached(any(), request) } returns 5
            val holder = holder(session, audio, video)
            holder.setPlayerTimeMs(491_203L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 100L)

            assertEquals(100L, testScheduler.currentTime)
            assertEquals(null, holder.pendingSegmentDemandSummary())
            verify(exactly = 1) { streamState.jumpBufferedTo(video, 101) }
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `segment demand pumps requested segment without forward jump`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, isAudio = true)
            val video = format(247, isAudio = false)
            val request = SabrSegmentRequest.media(audio, 35)
            val session = mockk<YoutubeSabrSession>()
            val streamState = mockk<YoutubeSabrStreamState>()
            every { session.streamState } returns streamState
            every { streamState.setActiveTrackTypes(true, true) } returns Unit
            every { session.setPlayHeadMs(any()) } returns Unit
            every { session.evictPlayed() } returns Unit
            every { session.requestNumber } returns 9
            every { session.cachedBytes } returns 0L
            every { session.diagnosticTrace } returns ""
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getMinBufferedEndMs() } returns 329_492L
            every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 340_000L) } returns 64
            every { streamState.getSegmentStartMs(audio, 35) } returns 340_000L
            every { streamState.getSegmentEndMs(audio, 34) } returns 339_476L
            every { streamState.getSegmentEndMs(audio, 35) } returns 349_461L
            every { streamState.getSegmentEndMs(video, 63) } returns 333_800L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.setActiveTrackTypes(false, true) } returns Unit
            every { streamState.setBufferedRangesOverride(any()) } returns Unit
            every { streamState.setBufferedRangesOverride(null) } returns Unit
            every { streamState.setSelectVideoFormatBeforeAudio(any()) } returns Unit
            every { session.pumpOnceStreamingUntilCached(any(), request) } returns 0
            val holder = holder(session, audio, video)
            holder.setPlayerTimeMs(340_000L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 0L)

            verify(exactly = 0) { session.prepareForForwardJump(request) }
            verify(exactly = 1) { session.pumpOnceStreamingUntilCached(any(), request) }
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
        every { format.lastModified } returns if (isAudio) AUDIO_LAST_MODIFIED else VIDEO_LAST_MODIFIED
        every { format.xtags } returns null
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

    private companion object {
        const val AUDIO_LAST_MODIFIED = 1_765_814_035_331_078L
        const val VIDEO_LAST_MODIFIED = 1_726_365_891_623_401L
    }
}
