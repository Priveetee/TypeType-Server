package dev.typetype.server

import dev.typetype.server.routes.withPlayableSabrStreams
import dev.typetype.server.services.SabrPreparedInfo
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SabrTokenBundle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class SabrStreamContractFilterTest {
    @Test
    fun `filter removes sabr streams when probe is unavailable`() = runTest {
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns null
        val progressive = testVideoStream(itag = 18)
        val sabrVideo = testVideoStream(itag = 299).copy(deliveryMethod = "sabr")
        val sabrAudio = testAudioStream(itag = 140, deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(progressive, sabrVideo),
            audioStreams = listOf(sabrAudio),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(progressive), filtered.videoOnlyStreams)
        assertEquals(emptyList<Any>(), filtered.audioStreams)
    }

    @Test
    fun `filter keeps only sabr streams resolved by sabr info`() = runTest {
        val audio = sabrFormat(itag = 140, isAudio = true, mimeType = "audio/mp4; codecs=\"mp4a.40.2\"")
        val video = sabrFormat(itag = 299, isAudio = false, mimeType = "video/mp4; codecs=\"avc1.64002a\"")
        val info = sabrInfo(listOf(audio, video))
        val store = mockk<SabrSessionStore>()
        coEvery { store.fetchInfo("video1", cachedFirst = true) } returns SabrPreparedInfo(info, token())
        val playableVideo = testVideoStream(itag = 299).copy(deliveryMethod = "sabr")
        val missingVideo = testVideoStream(itag = 298).copy(deliveryMethod = "sabr")
        val playableAudio = testAudioStream(itag = 140, deliveryMethod = "sabr")
        val missingAudio = testAudioStream(itag = 141, deliveryMethod = "sabr")

        val filtered = testStreamResponse(
            videoOnlyStreams = listOf(playableVideo, missingVideo),
            audioStreams = listOf(playableAudio, missingAudio),
        ).withPlayableSabrStreams("https://youtube.com/watch?v=video1", store)

        assertEquals(listOf(playableVideo), filtered.videoOnlyStreams)
        assertEquals(listOf(playableAudio), filtered.audioStreams)
    }

    private fun sabrInfo(formats: List<YoutubeSabrFormat>): YoutubeSabrInfo {
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns formats
        every { info.findFormatByItag(any()) } answers {
            val itag = firstArg<Int>()
            formats.firstOrNull { it.itag == itag }
        }
        return info
    }

    private fun sabrFormat(itag: Int, isAudio: Boolean, mimeType: String): YoutubeSabrFormat {
        val format = mockk<YoutubeSabrFormat>()
        every { format.itag } returns itag
        every { format.getItag() } returns itag
        every { format.isAudio } returns isAudio
        every { format.isVideo } returns !isAudio
        every { format.mimeType } returns mimeType
        every { format.audioTrackId } returns null
        every { format.isOriginalAudio } returns true
        every { format.isDrc } returns false
        every { format.xtags } returns null
        every { format.bitrate } returns 128000
        every { format.height } returns if (isAudio) 0 else 1080
        return format
    }

    private fun token(): SabrTokenBundle = SabrTokenBundle(
        videoId = "video1",
        visitorBoundPoToken = "visitor",
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = "visitor-data",
        videoBoundPoToken = "video",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )
}
