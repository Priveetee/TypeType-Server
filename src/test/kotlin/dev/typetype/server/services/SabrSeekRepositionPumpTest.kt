package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSeekRepositionPumpTest {
    @Test
    fun `missing audio anchors rewind when edge is between paired starts`() {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val videoRequest = SabrSegmentRequest.media(video, 24)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            val state = mockk<YoutubeSabrStreamState>(relaxed = true)
            every { session.streamState } returns state
            every { session.getCachedSegment(any()) } returns null
            every { state.getSegmentNumberAtOrAfterTimeMs(video, 120_000L) } returns 24
            every { state.getSegmentNumberAtOrAfterTimeMs(audio, 120_000L) } returns 13
            every { state.getSegmentStartMs(video, 24) } returns 119_604L
            every { state.getSegmentStartMs(audio, 13) } returns 118_979L
            every { state.getMinBufferedEndMs() } returns 119_200L
            every {
                session.getCachedSegment(match { it.format.itag == videoRequest.format.itag && it.sequenceNumber == 24 })
            } returns mockk<SabrMediaSegment>()
            val holder = holder(session, audio, video)
            val store = mockk<SabrSessionStore>(relaxed = true)

            SabrPlaybackSessionService(store).seekExisting(holder, 120_000L)

            assertEquals("140:13", holder.pendingSegmentDemandSummary())
            val seek = holder.consumeRefetch()
            assertEquals(140, seek?.format?.itag)
            assertEquals(13, seek?.sequenceNumber)
        } finally {
            SabrSegmentDemandTracker.clearAll()
        }
    }

    @Test
    fun `forward seek uses target demand pump without normal backoff`() = runTest {
        SabrSegmentDemandTracker.clearAll()
        try {
            val audio = format(140, true)
            val video = format(137, false)
            val request = SabrSegmentRequest.media(video, 24)
            val session = mockk<YoutubeSabrSession>(relaxed = true)
            every { session.streamState } returns mockk(relaxed = true)
            every { session.getCachedSegment(any()) } returns null
            every { session.pumpOnceStreamingForDemand(any(), request) } returns mockk(relaxed = true)
            val holder = holder(session, audio, video)
            holder.requestSegmentDemand(request)
            holder.requestForwardSeek(request)
            var rounds = 0

            SabrSessionPumpLoop().run({ rounds++ < 1 }, holder, intervalMs = 0L)

            verify(exactly = 1) { session.prepareForForwardJump(request) }
            verify(exactly = 0) { session.pumpOnceStreaming(any()) }
            verify(exactly = 1) { session.pumpOnceStreamingForDemand(any(), request) }
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
        every { format.audioTrackId } returns null
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }
}
