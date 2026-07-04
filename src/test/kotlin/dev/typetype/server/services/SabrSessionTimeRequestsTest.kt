package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrSessionTimeRequestsTest {
    @Test
    fun `mediaRequestsAt returns active audio and video requests for player time`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 321_601L) } returns 64
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 321_601L) } returns 33
        val holder = holder(session, audio, video)

        val requests = holder.mediaRequestsAt(321_601L)

        assertEquals(listOf(137, 140), requests.map { it.format.itag })
        assertEquals(listOf(64, 33), requests.map { it.sequenceNumber })
    }

    @Test
    fun `mediaRequestsAt excludes inactive tracks`() {
        val audio = sabrFormat(itag = 140, isAudio = true)
        val video = sabrFormat(itag = 137, isAudio = false)
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { session.streamState } returns state
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 321_601L) } returns 64
        val holder = holder(session, audio, video)
        holder.setActiveTracks(videoActive = true, audioActive = false)

        val requests = holder.mediaRequestsAt(321_601L)

        assertEquals(listOf(137), requests.map { it.format.itag })
        assertEquals(listOf(64), requests.map { it.sequenceNumber })
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        return format
    }

    private fun holder(
        session: YoutubeSabrSession,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
    ): SabrSessionHolder = SabrSessionHolder(
        session,
        mockk<YoutubeSabrInfo>(),
        audio,
        video,
        "session",
        SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
        Instant.now(),
    )
}
