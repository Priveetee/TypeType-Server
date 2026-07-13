package dev.typetype.server.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SabrPumpLauncherTest {
    @Test
    fun `completed network pump can be started again`() = runTest {
        val pump = mockk<SabrSessionPump>()
        val registry = mockk<SabrSessionRegistry>()
        val holder = holder()
        holder.recordNetworkFailure("timeout")
        every { registry.contains(holder.key) } returns true
        coEvery { pump.pumpLoop(any(), holder, 100L) } returns Unit

        launchSabrPump(pump, registry, holder, 100L)
        advanceUntilIdle()

        assertEquals(SabrPlaybackState.IDLE, holder.playbackState())
        assertTrue(holder.markPumpStarted())
        holder.markPumpStopped()
        coVerify(exactly = 1) { pump.pumpLoop(any(), holder, 100L) }
    }

    private fun holder(): SabrSessionHolder {
        val session = mockk<YoutubeSabrSession>(relaxed = true)
        val audio = format(140, true)
        val video = format(137, false)
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
        return format
    }
}
