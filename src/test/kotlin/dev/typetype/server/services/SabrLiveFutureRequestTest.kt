package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLiveFutureRequestTest {
    @Test
    fun `segment behind the reported live head is not treated as future`() {
        val audio = format(140)
        val video = format(299)
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        every { session.streamState } returns state
        every { session.isLive } returns true
        every { session.liveHeadSequenceNumber } returns 230L
        every { session.getCachedSegment(any()) } returns null
        every { state.isLive } returns true
        every { state.liveHeadSequenceNumber } returns 230L
        every { state.liveHeadTimeMs } returns 1_060_000L
        val holder = SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )

        assertFalse(holder.isFutureLiveRequest(SabrSegmentRequest.media(video, 201)))
    }

    private fun format(itag: Int): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
    }
}
