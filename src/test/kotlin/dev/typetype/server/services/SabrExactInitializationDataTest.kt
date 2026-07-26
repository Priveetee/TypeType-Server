package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class SabrExactInitializationDataTest {
    @Test
    fun `exact Android initialization uses the video-bound token and publishes the index`() = runTest {
        val audio = format(140, true)
        val video = format(137, false)
        val token = byteArrayOf(1, 2, 3)
        val bytes = byteArrayOf(4, 5, 6)
        val indexed = AtomicBoolean()
        val state = mockk<YoutubeSabrStreamState>(relaxed = true) {
            every { poToken } returns token
            every { hasSegmentIndex(video) } answers { indexed.get() }
        }
        val sabr = mockk<YoutubeSabrSession>(relaxed = true) {
            every { streamState } returns state
            every {
                fetchInitializationData(video, any<Localization>(), 3_500L, match { it.contentEquals(token) })
            } answers {
                indexed.set(true)
                bytes
            }
        }
        val holder = SabrSessionHolder(
            sabr,
            mockk<YoutubeSabrInfo>(),
            audio,
            video,
            "android-session",
            SabrSessionKey(
                "exact-init-video",
                "user",
                140,
                null,
                137,
                0L,
                SabrSessionPurpose.ANDROID_PLAYBACK,
            ),
            Instant.EPOCH,
        )

        val result = SabrInitializationData.fetchExact(holder, video, 3_500L)

        assertArrayEquals(bytes, result)
        assertTrue(state.hasSegmentIndex(video))
        verify(exactly = 1) {
            sabr.fetchInitializationData(video, any<Localization>(), 3_500L, match { it.contentEquals(token) })
        }
    }

    private fun format(itag: Int, audio: Boolean): YoutubeSabrFormat = mockk {
        every { this@mockk.itag } returns itag
        every { isAudio } returns audio
        every { lastModified } returns 1L
        every { xtags } returns null
        every { mimeType } returns if (audio) "audio/mp4" else "video/mp4"
        every { audioTrackId } returns null
    }
}
