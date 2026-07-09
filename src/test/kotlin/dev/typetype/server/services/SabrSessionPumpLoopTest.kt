package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionPumpLoopTest {
    @Test
    fun `segment demand pumps with targeted request shape`() = runTest {
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
            every { session.getCachedSegment(any()) } returns null
            every { streamState.getMinBufferedEndMs() } returns 329_492L
            every { streamState.getSegmentNumberAtOrAfterTimeMs(video, 340_000L) } returns 64
            every { streamState.getSegmentStartMs(audio, 35) } returns 340_000L
            every { streamState.getSegmentEndMs(audio, 34) } returns 339_476L
            every { streamState.getSegmentEndMs(video, 63) } returns 333_800L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.setBufferedRangesOverride(any()) } returns Unit
            every { streamState.setBufferedRangesOverride(null) } returns Unit
            every { streamState.setSelectVideoFormatBeforeAudio(any()) } returns Unit
            every { session.prepareForForwardJump(request) } returns Unit
            every { session.pumpOnceStreamingUntilCached(any(), request) } returns 0
            val holder = holder(session, audio, video)
            holder.setPlayerTimeMs(340_000L)
            holder.requestSegmentDemand(request)
            var rounds = 0

            SabrSessionPump().pumpLoop({ rounds++ == 0 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForForwardJump(request) }
            verify(exactly = 1) { session.pumpOnceStreamingUntilCached(any(), request) }
            verifyOrder {
                streamState.setSelectVideoFormatBeforeAudio(false)
                streamState.setBufferedRangesOverride(match { ranges ->
                    ranges.size == 2 &&
                        ranges[0].summarize() == "itag=140:seq=1-34:time=0+339476:timescale=1000" &&
                        ranges[1].summarize() == "itag=247:seq=1-63:time=0+333800:timescale=1000"
                })
                session.pumpOnceStreamingUntilCached(any(), request)
                streamState.setBufferedRangesOverride(null)
                streamState.setActiveTrackTypes(true, true)
            }
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

    private companion object {
        const val AUDIO_LAST_MODIFIED = 1_765_814_035_331_078L
        const val VIDEO_LAST_MODIFIED = 1_726_365_891_623_401L
    }
}
