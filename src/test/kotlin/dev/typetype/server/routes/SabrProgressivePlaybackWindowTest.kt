package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrProgressivePlaybackWindowTest {
    @Test
    fun `vod window exposes timeline segments before their payload completes`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(308, isAudio = false)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { state.getSegmentNumberAtOrAfterTimeMs(any(), any()) } returns 1
        every { state.getSegmentStartMs(any(), 1) } returns 0L
        every { state.getSegmentEndMs(audio, 1) } returns 9_985L
        every { state.getSegmentEndMs(video, 1) } returns 4_000L
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audioSegment = readableSegment(audio, durationMs = 9_985L)
        val videoSegment = readableSegment(video, durationMs = 4_000L)
        every { session.streamState } returns state
        every { session.getReadableSegment(match { it.format == audio }) } returns audioSegment
        every { session.getReadableSegment(match { it.format == video }) } returns videoSegment
        every { session.getCachedSegment(any()) } returns null
        val holder = holder(session, audio, video)
        val store = mockk<SabrSessionStore>()
        coEvery { store.cachedSegment(holder, any()) } returns null
        val result = SabrPlaybackWindowBuilder(store).build(
            holder,
            SabrPlaybackWindowRequest(0L, 0L, video.itag, audio.itag, bufferGoalMs = 2_500L),
        )

        assertTrue(result.isReady)
        assertTrue(result.blockedRequests.isEmpty())
        assertFalse(audioSegment.isComplete)
        assertFalse(videoSegment.isComplete)
        assertEquals(4_000L, requireNotNull(result.response.video).segments.single().durationMs)
        assertEquals(9_985L, result.response.audio.segments.single().durationMs)
    }

    private fun readableSegment(format: YoutubeSabrFormat, durationMs: Long): SabrMediaSegment {
        val header = mockk<SabrMediaHeader>()
        every { header.itag } returns format.itag
        every { header.isInitSegment } returns false
        every { header.sequenceNumber } returns 1
        every { header.startMs } returns 0L
        every { header.durationMs } returns durationMs
        return mockk<SabrMediaSegment>().also {
            every { it.header } returns header
            every { it.isComplete } returns false
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
        sessionToken = "session",
        key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        lastRequestAt = Instant.EPOCH,
    )

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat = mockk<YoutubeSabrFormat>().also {
        every { it.itag } returns itag
        every { it.isAudio } returns isAudio
        every { it.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { it.approxDurationMs } returns 900_000L
    }
}
