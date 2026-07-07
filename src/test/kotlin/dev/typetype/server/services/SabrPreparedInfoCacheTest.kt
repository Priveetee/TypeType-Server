package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class SabrPreparedInfoCacheTest {
    @Test
    fun `prepared info reports audio and video only when both are present`() {
        assertFalse(preparedInfo(listOf(format(isAudio = false))).hasAudioAndVideoFormats())
        assertFalse(preparedInfo(listOf(format(isAudio = true))).hasAudioAndVideoFormats())
        assertTrue(preparedInfo(listOf(format(isAudio = true), format(isAudio = false))).hasAudioAndVideoFormats())
    }

    @Test
    fun `cache can remove incomplete prepared info`() {
        val cache = SabrPreparedInfoCache()
        cache.put("video", 120_000L, preparedInfo(listOf(format(isAudio = false))))

        cache.remove("video", 120_000L)

        assertFalse(cache.get("video", 120_000L)?.hasAudioAndVideoFormats() == true)
    }

    @Test
    fun `cache separates prepared info by start window`() {
        val cache = SabrPreparedInfoCache()
        cache.put("video", 120_000L, preparedInfo(listOf(format(isAudio = true), format(isAudio = false))))

        assertTrue(cache.get("video", 120_000L)?.hasAudioAndVideoFormats() == true)
        assertFalse(cache.get("video", 340_000L)?.hasAudioAndVideoFormats() == true)
    }

    private fun preparedInfo(formats: List<YoutubeSabrFormat>): SabrPreparedInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns formats
        return SabrPreparedInfo(info, token())
    }

    private fun format(isAudio: Boolean): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        return format
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = "visitor",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
