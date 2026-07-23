package dev.typetype.server.services

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant

class AndroidPlaybackServiceTest {
    @Test
    fun `seek keeps session and complete presentation while advancing generation`() {
        val audio = format(140, audio = true)
        val video = format(137, audio = false)
        val state = state(audio, video)
        val holder = holder(audio, video, state)
        val store = mockk<SabrSessionStore> {
            every { startPump(holder) } returns Unit
        }
        val service = AndroidPlaybackService(store, mockk(relaxed = true))

        val result = service.seek(holder, generation = 0L, playerTimeMs = 45_000L) as AndroidPlaybackSeekResult.Ready

        assertSame(holder, result.holder)
        assertEquals(1L, holder.activeGeneration())
        val manifest = result.manifest as AndroidDashManifestResult.Ready
        assertEquals(60_000L, manifest.durationMs)
        assertEquals(true, manifest.manifest.contains("<S t=\"0\""))
        assertEquals(true, manifest.manifest.contains("generation=1"))
        verify(exactly = 1) { store.startPump(holder) }
    }

    @Test
    fun `stale seek does not change the active generation`() {
        val audio = format(140, audio = true)
        val video = format(137, audio = false)
        val holder = holder(audio, video, state(audio, video))
        holder.advancePlaybackGeneration(1_000L)
        val service = AndroidPlaybackService(mockk(relaxed = true), mockk(relaxed = true))

        assertEquals(AndroidPlaybackSeekResult.StaleGeneration, service.seek(holder, 0L, 30_000L))
        assertEquals(1L, holder.activeGeneration())
    }

    @Test
    fun `active live creation is rejected before allocating a session`() = runTest {
        val store = mockk<SabrSessionStore>(relaxed = true)
        val service = AndroidPlaybackService(store, mockk(relaxed = true))
        val prepared = SabrPreparedInfo(mockk<YoutubeSabrInfo>(), null, isLive = true)

        val result = service.create(
            "video",
            "user",
            prepared,
            format(140, true),
            format(137, false),
            AndroidSubtitleInventoryHandle.ready(emptyList()),
            deferredSubtitles = false,
        )

        assertEquals(AndroidPlaybackCreateResult.UnsupportedLive, result)
        coVerify(exactly = 0) { store.fetchInitializationData(any(), any()) }
        verify(exactly = 0) { store.getOrCreate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun holder(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        state: YoutubeSabrStreamState,
    ): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true) {
            every { streamState } returns state
            every { getCachedSegment(any()) } returns null
        }
        return SabrSessionHolder(
            session,
            mockk(),
            audio,
            video,
            "android-session",
            SabrSessionKey("video", "user", 140, null, 137, 0L, SabrSessionPurpose.ANDROID_PLAYBACK),
            Instant.EPOCH,
        )
    }

    private fun state(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): YoutubeSabrStreamState = mockk(relaxed = true) {
        every { hasSegmentIndex(audio) } returns true
        every { hasSegmentIndex(video) } returns true
        every { getEndSegment(any()) } returns 60L
        every { getSegmentStartMs(any(), any()) } answers { (secondArg<Int>() - 1L) * 1_000L }
        every { getSegmentEndMs(any(), any()) } answers { secondArg<Int>() * 1_000L }
        every { getSegmentNumberAtOrAfterTimeMs(any(), 45_000L) } returns 46
        every { getSegmentStartMs(any(), 46) } returns 45_000L
        every { getMinBufferedEndMs() } returns 0L
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
}
