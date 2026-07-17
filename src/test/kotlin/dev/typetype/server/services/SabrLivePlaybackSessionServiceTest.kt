package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class SabrLivePlaybackSessionServiceTest {
    @Test
    fun `live prepare warms metadata and starts behind the live head`() = runTest {
        val audio = format(140, isAudio = true)
        val video = format(137, isAudio = false)
        val info = mockk<YoutubeSabrInfo>()
        val prepared = SabrPreparedInfo(info, token(), isLive = true, isLiveContent = true)
        val holder = holder(audio, video)
        val state = holder.session.streamState
        every { holder.session.isLive } returns true
        every { holder.session.liveHeadSequenceNumber } returns 200L
        every { state.isLive } returns true
        every { state.isPostLiveDvr } returns false
        every { state.liveHeadSequenceNumber } returns 200L
        every { state.liveHeadTimeMs } returns 1_005_000L
        every { state.getMaxSegment(audio) } returns 100
        every { state.getMaxSegment(video) } returns 200
        every { state.getSegmentEndMs(audio, 100) } returns 1_000_000L
        every { state.getSegmentEndMs(video, 200) } returns 1_002_000L
        every { state.getBufferedEndMs(audio) } returns 1_000_000L
        every { state.getBufferedEndMs(video) } returns 1_002_000L
        every { state.getMinBufferedEndMs() } returns 1_000_000L
        every { state.getSegmentNumberAtOrAfterTimeMs(video, 995_000L) } returns 198
        every { state.getSegmentNumberAtOrAfterTimeMs(audio, 995_000L) } returns 99
        every { state.getSegmentStartMs(video, 198) } returns 990_000L
        every { state.getSegmentStartMs(audio, 99) } returns 990_000L
        val store = mockk<SabrSessionStore>()
        every {
            store.getOrCreate(
                "video",
                "user",
                info,
                audio,
                video,
                prepared.initialToken,
                0L,
                false,
                SabrSessionPurpose.PLAYBACK,
                false,
            )
        } returns holder
        coEvery { store.ensureWarmed(holder, 8) } returns Unit
        every { store.startPump(holder) } returns Unit

        val result = SabrPlaybackSessionService(store).prepare("video", "user", prepared, audio, video, 0L)

        assertEquals(995_000L, result.startTimeMs)
        assertEquals(995_000L, holder.playerTimeMs())
        assertTrue(holder.expectsLive())
        coVerify(exactly = 1) { store.ensureWarmed(holder, 8) }
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
        verify(exactly = 1) { store.startPump(holder) }
    }

    private fun holder(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true)
        every { session.streamState } returns state
        every { session.getCachedSegment(any()) } returns null
        every { session.isBeyondEnd(any()) } returns false
        every { session.prepareForInitialization(any()) } returns Unit
        every { state.setActiveTrackTypes(any(), any()) } returns Unit
        every { state.setSelectVideoFormatBeforeAudio(any()) } returns Unit
        return SabrSessionHolder(
            session = session,
            info = mockk<YoutubeSabrInfo>(),
            audioFormat = audio,
            videoFormat = video,
            sessionToken = "session-token",
            key = SabrSessionKey("video", "user", audio.itag, null, video.itag, 0L),
            lastRequestAt = Instant.EPOCH,
        )
    }

    private fun format(itag: Int, isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isAudio } returns isAudio
        every { format.audioTrackId } returns null
        every { format.mimeType } returns if (isAudio) "audio/mp4" else "video/mp4"
        every { format.bitrate } returns if (isAudio) 128_000 else 2_000_000
        return format
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor-token",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
