package dev.typetype.server.routes

import dev.typetype.server.services.SABR_TOKEN_BINDING_FAILURE
import dev.typetype.server.services.SABR_RECOVERABLE_FAILURE_PREFIX
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionKey
import dev.typetype.server.services.SabrSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class SabrPlaybackRecoveryTest {
    @Test
    fun `network failure invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns null
        every { holder.networkFailure() } returns "timeout"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `recoverable media failure invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "$SABR_RECOVERABLE_FAILURE_PREFIX Unexpected EOF"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit

        assertEquals("retry_fresh_session", SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `local spool failure does not request a fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "Could not write SABR spool file"

        assertNull(SabrPlaybackRecovery(store).action(holder))
        coVerify(exactly = 0) { store.invalidatePlaybackInfo(any()) }
    }

    @Test
    fun `stalled demand invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "SABR demand stalled for 140:39"
        every { holder.key } returns SabrSessionKey("video", "user", 140, "en-US.4", 137, 379_441L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `unauthorized token refresh invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "SABR upstream unauthorized HTTP 403 after TypeType token refresh"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `token binding mismatch invalidates playback info and requests fresh session`() = runTest {
        val holder = mockk<SabrSessionHolder>()
        val store = mockk<SabrSessionStore>()
        every { holder.terminalFailure() } returns "video:137:12 $SABR_TOKEN_BINDING_FAILURE"
        every { holder.key } returns SabrSessionKey("video", "user", 140, null, 137, 0L)
        coEvery { store.invalidatePlaybackInfo("video") } returns Unit
        val recovery = SabrPlaybackRecovery(store)

        assertEquals("retry_fresh_session", recovery.action(holder))
        coVerify(exactly = 1) { store.invalidatePlaybackInfo("video") }
    }

    @Test
    fun `protected no-media retries only known strictly lower video ranks`() {
        val current = format(itag = 137, width = 1920, height = 1080, bitrate = 4_000_000)
        val formats = listOf(
            format(itag = 313, width = 3840, height = 2160, bitrate = 15_000_000),
            format(itag = 399, width = 1920, height = 1080, bitrate = 5_000_000),
            current,
            format(itag = 248, width = 1920, height = 1080, bitrate = 3_000_000),
            format(itag = 247, width = 1920, height = 1080, bitrate = 4_000_000),
            format(itag = 136, width = 1280, height = 720, bitrate = 2_500_000),
            format(itag = 136, width = 854, height = 480, bitrate = 1_000_000),
            format(itag = 135, width = 854, height = 480, bitrate = 1_000_000),
            format(itag = 134, width = 0, height = 360, bitrate = 500_000),
            format(itag = 140, width = 0, height = 0, bitrate = 128_000, isVideo = false),
        )
        val recovery = SabrPlaybackRecovery(mockk())

        assertEquals(listOf(248, 136, 135), recovery.retryVideoItags(holder(current, formats)))
    }

    @Test
    fun `protected no-media excludes retries when current rank is unknown`() {
        val current = format(itag = 137, width = 1920, height = 0, bitrate = 4_000_000)
        val lower = format(itag = 136, width = 1280, height = 720, bitrate = 2_500_000)
        val recovery = SabrPlaybackRecovery(mockk())

        assertEquals(emptyList<Int>(), recovery.retryVideoItags(holder(current, listOf(current, lower))))
    }

    @Test
    fun `non protected failure has no retry video itags`() {
        val current = format(itag = 137, width = 1920, height = 1080, bitrate = 4_000_000)
        val lower = format(itag = 136, width = 1280, height = 720, bitrate = 2_500_000)
        val holder = holder(current, listOf(current, lower), "SABR demand stalled for 140:39")
        val recovery = SabrPlaybackRecovery(mockk())

        assertEquals(emptyList<Int>(), recovery.retryVideoItags(holder))
    }

    private fun holder(
        current: YoutubeSabrFormat,
        formats: List<YoutubeSabrFormat>,
        failure: String = "video:${current.itag}:12 status=3 protected no-media",
    ): SabrSessionHolder {
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns formats
        val holder = mockk<SabrSessionHolder>()
        every { holder.terminalFailure() } returns failure
        every { holder.videoFormat } returns current
        every { holder.info } returns info
        return holder
    }

    private fun format(
        itag: Int,
        width: Int,
        height: Int,
        bitrate: Int,
        isVideo: Boolean = true,
    ): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.isVideo } returns isVideo
        every { format.width } returns width
        every { format.height } returns height
        every { format.bitrate } returns bitrate
        return format
    }
}
