package dev.typetype.server.services

import dev.typetype.server.testStreamResponse
import dev.typetype.server.models.ExtractionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

class SabrFallbackStreamServiceTest {
    @Test
    fun `enriches empty youtube extraction with token sabr formats`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val response = testStreamResponse(videoOnlyStreams = emptyList(), audioStreams = emptyList())
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)
        coEvery { sessionStore.fetchInfo(VIDEO_ID, cachedFirst = true) } returns preparedInfo()
        val service = SabrFallbackStreamService(delegate, sessionStore)

        val result = service.getStreamInfo(YOUTUBE_URL)

        val enriched = (result as ExtractionResult.Success).data
        assertEquals(listOf(137), enriched.videoOnlyStreams.map { it.itag })
        assertEquals(listOf(140), enriched.audioStreams.map { it.itag })
        assertEquals("sabr", enriched.videoOnlyStreams.single().deliveryMethod)
        assertEquals("sabr", enriched.audioStreams.single().deliveryMethod)
        assertEquals("fr-FR.4", enriched.originalAudioTrackId)
        assertTrue(enriched.audioStreams.single().sabrSessionUrl?.contains("audioTrackId=fr-FR.4") == true)
    }

    @Test
    fun `keeps already playable extraction without token request`() = runTest {
        val delegate = mockk<StreamService>()
        val sessionStore = mockk<SabrSessionStore>()
        val response = testStreamResponse()
        coEvery { delegate.getStreamInfo(YOUTUBE_URL) } returns ExtractionResult.Success(response)
        val service = SabrFallbackStreamService(delegate, sessionStore)

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertEquals(ExtractionResult.Success(response), result)
        coVerify(exactly = 0) { sessionStore.fetchInfo(any(), any(), any()) }
    }

    private fun preparedInfo(): SabrPreparedInfo {
        val video = mockk<YoutubeSabrFormat>(relaxed = true)
        every { video.isVideo } returns true
        every { video.isAudio } returns false
        every { video.itag } returns 137
        every { video.mimeType } returns "video/mp4; codecs=\"avc1.640028\""
        every { video.qualityLabel } returns "1080p"
        every { video.width } returns 1920
        every { video.height } returns 1080
        every { video.bitrate } returns 4_000_000
        val audio = mockk<YoutubeSabrFormat>(relaxed = true)
        every { audio.isVideo } returns false
        every { audio.isAudio } returns true
        every { audio.itag } returns 140
        every { audio.mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
        every { audio.audioQuality } returns "AUDIO_QUALITY_MEDIUM"
        every { audio.audioTrackId } returns "fr-FR.4"
        every { audio.audioTrackDisplayName } returns "French (original)"
        every { audio.isOriginalAudio } returns true
        every { audio.isAudioDefault } returns true
        every { audio.bitrate } returns 129_000
        val info = mockk<YoutubeSabrInfo>()
        every { info.formats } returns listOf(video, audio)
        return SabrPreparedInfo(info, null)
    }

    private companion object {
        const val VIDEO_ID = "Vj6ReOur1Kk"
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"
    }
}
