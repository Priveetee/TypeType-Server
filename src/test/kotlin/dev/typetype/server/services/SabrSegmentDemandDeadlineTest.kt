package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSegmentDemandDeadlineTest {
    @AfterEach
    fun clearDemands(): Unit = SabrSegmentDemandTracker.clearAll()

    @Test
    fun `repeated requests preserve deadline and terminate stalled demand`() {
        val audio = mockk<YoutubeSabrFormat>()
        val video = mockk<YoutubeSabrFormat>()
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val streamState = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { audio.itag } returns 140
        every { video.itag } returns 137
        every { session.streamState } returns streamState
        every { session.getCachedSegment(any()) } returns null
        every { streamState.getSegmentStartMs(audio, 39) } returns 379_414L
        val holder = holder(session, audio, video)
        val request = SabrSegmentRequest.media(audio, 39)

        holder.requestSegmentDemand(request, nowMs = 1_000L)
        holder.requestSegmentDemand(request, nowMs = 12_000L)

        assertFalse(holder.failExpiredSegmentDemand(nowMs = 15_999L))
        assertTrue(holder.failExpiredSegmentDemand(nowMs = 16_000L))
        assertEquals(SabrPlaybackState.TERMINAL, holder.playbackState())
        assertEquals("SABR demand stalled for 140:39", holder.terminalFailure())
        assertNull(holder.pendingSegmentDemandSummary())
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
}
