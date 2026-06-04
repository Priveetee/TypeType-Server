package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.StreamService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestServiceAudioTest {
    private val streamService: StreamService = mockk()
    private val service = ManifestService(streamService)

    @Test
    fun `mono-track audio produces one AdaptationSet per mimeType without lang or label`() = runBlocking {
        val aac = testAudioStream(format = "M4A", codec = "mp4a.40.2")
        val opus = testAudioStream(format = "WEBM", codec = "opus", url = "https://example.com/opus")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(audioStreams = listOf(aac, opus)))

        val xml = (service.dashManifest("https://youtube.com/watch?v=test") as ExtractionResult.Success).data

        val sets = Regex("<AdaptationSet[^>]*mimeType").findAll(xml).count()
        assertTrue(sets >= 2)
        assertTrue(!xml.contains("lang="))
        assertTrue(!xml.contains("label="))
    }

    @Test
    fun `multi-track audio produces one AdaptationSet per trackId and mimeType with lang and label`() = runBlocking {
        val en = testAudioStream(format = "M4A", codec = "mp4a.40.2", url = "https://example.com/en",
            audioTrackId = "en.0", audioTrackName = "English", audioLocale = "en")
        val fr = testAudioStream(format = "M4A", codec = "mp4a.40.2", url = "https://example.com/fr",
            audioTrackId = "fr.0", audioTrackName = "French", audioLocale = "fr")
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(audioStreams = listOf(en, fr)))

        val xml = (service.dashManifest("https://youtube.com/watch?v=test") as ExtractionResult.Success).data

        assertTrue(xml.contains("lang=\"en\""))
        assertTrue(xml.contains("lang=\"fr\""))
        assertTrue(xml.contains("label=\"English\""))
        assertTrue(xml.contains("label=\"French\""))
        val sets = Regex("<AdaptationSet[^>]*mimeType").findAll(xml).count()
        assertTrue(sets >= 2)
    }

    @Test
    fun `null audioLocale and audioTrackName produce no lang or label attributes`() = runBlocking {
        val track = testAudioStream(format = "M4A", codec = "mp4a.40.2",
            audioTrackId = "en.0", audioTrackName = null, audioLocale = null)
        coEvery { streamService.getStreamInfo(any()) } returns
            ExtractionResult.Success(testStreamResponse(audioStreams = listOf(track)))

        val xml = (service.dashManifest("https://youtube.com/watch?v=test") as ExtractionResult.Success).data

        assertTrue(!xml.contains("lang="))
        assertTrue(!xml.contains("label="))
    }
}
