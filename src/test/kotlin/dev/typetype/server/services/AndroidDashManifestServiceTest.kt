package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

class AndroidDashManifestServiceTest {
    @Test
    fun `complete exact indexes produce a full static vod manifest`() {
        val fixture = fixture(audioCount = 150, videoCount = 150, segmentMs = 2_000L)

        val result = AndroidDashManifestService().build(fixture.holder) as AndroidDashManifestResult.Ready

        assertEquals(300_000L, result.durationMs)
        assertTrue(result.manifest.contains("mediaPresentationDuration=\"PT300.000S\""))
        assertTrue(result.manifest.contains("<S t=\"0\" d=\"2000\" r=\"149\""))
    }

    @Test
    fun `player and reader positions cannot truncate the vod manifest`() {
        val fixture = fixture(audioCount = 60, videoCount = 60, segmentMs = 1_000L)
        every { fixture.holder.playerTimeMs() } returns 45_000L
        every { fixture.holder.readerHeadMs() } returns 50_000L
        every { fixture.holder.readerTailMs() } returns 48_000L

        val result = AndroidDashManifestService().build(fixture.holder) as AndroidDashManifestResult.Ready

        assertEquals(60_000L, result.durationMs)
        assertTrue(result.manifest.contains("<S t=\"0\""))
    }

    @Test
    fun `unknown exact index stays preparing instead of publishing partial xml`() {
        val fixture = fixture(audioCount = 5, videoCount = 5, segmentMs = 1_000L)
        every { fixture.state.hasSegmentIndex(fixture.audio) } returns false

        assertEquals(AndroidDashManifestResult.Preparing, AndroidDashManifestService().build(fixture.holder))
    }

    @Test
    fun `incomplete track coverage is rejected`() {
        val fixture = fixture(audioCount = 10, videoCount = 4, segmentMs = 1_000L)

        assertInstanceOf(
            AndroidDashManifestResult.Invalid::class.java,
            AndroidDashManifestService().build(fixture.holder),
        )
    }

    @Test
    fun `active live content is explicitly unsupported`() {
        val fixture = fixture(audioCount = 5, videoCount = 5, segmentMs = 1_000L)
        every { fixture.holder.expectsLive() } returns true

        assertEquals(AndroidDashManifestResult.UnsupportedLive, AndroidDashManifestService().build(fixture.holder))
    }

    private fun fixture(audioCount: Int, videoCount: Int, segmentMs: Long): Fixture {
        val audio = format(140, audio = true)
        val video = format(137, audio = false)
        val state = mockk<YoutubeSabrStreamState>()
        every { state.hasSegmentIndex(audio) } returns true
        every { state.hasSegmentIndex(video) } returns true
        every { state.getEndSegment(audio) } returns audioCount.toLong()
        every { state.getEndSegment(video) } returns videoCount.toLong()
        every { state.getSegmentStartMs(any(), any()) } answers { (secondArg<Int>() - 1L) * segmentMs }
        every { state.getSegmentEndMs(any(), any()) } answers { secondArg<Int>() * segmentMs }
        val session = mockk<YoutubeSabrSession> { every { streamState } returns state }
        val holder = mockk<SabrSessionHolder> {
            every { this@mockk.session } returns session
            every { audioFormat } returns audio
            every { videoFormat } returns video
            every { sessionToken } returns "session-token"
            every { activeGeneration() } returns 0L
            every { expectsLive() } returns false
        }
        return Fixture(holder, state, audio)
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { isVideo } returns !audio
        every { mimeType } returns if (audio) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.640028\""
        every { bitrate } returns if (audio) 128_000 else 2_000_000
        every { width } returns if (audio) 0 else 1920
        every { height } returns if (audio) 0 else 1080
    }

    private data class Fixture(
        val holder: SabrSessionHolder,
        val state: YoutubeSabrStreamState,
        val audio: YoutubeSabrFormat,
    )
}
