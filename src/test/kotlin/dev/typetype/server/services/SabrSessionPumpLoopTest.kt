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
            every { session.getCachedSegment(request) } returns null
            every { streamState.getMinBufferedEndMs() } returns 329_492L
            every { streamState.getSegmentStartMs(audio, 35) } returns 340_000L
            every { streamState.setPlayerTimeMs(any()) } returns Unit
            every { streamState.setLastOnlyRange(audio, true) } returns Unit
            every { streamState.setLastOnlyRange(audio, false) } returns Unit
            every { streamState.setFullyBuffered(video, true) } returns Unit
            every { streamState.setFullyBuffered(video, false) } returns Unit
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
                streamState.setSelectVideoFormatBeforeAudio(true)
                streamState.setLastOnlyRange(audio, true)
                streamState.setFullyBuffered(video, true)
                session.pumpOnceStreamingUntilCached(any(), request)
                streamState.setFullyBuffered(video, false)
                streamState.setLastOnlyRange(audio, false)
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
        return format
    }
}
