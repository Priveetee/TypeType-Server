package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.testStreamResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe

class YoutubePlayerClientFallbackStreamServiceTest {
    @Test
    fun `classic extraction falls back between classic clients only`() = runBlocking {
        NewPipe.setYoutubePlayerClient(YoutubePlayerClient.MWEB.value)
        val observed = mutableListOf<String>()
        val success = ExtractionResult.Success(testStreamResponse().copy(id = YOUTUBE_URL))
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                val client = NewPipe.getYoutubePlayerClient()
                observed += client
                return if (client == YoutubePlayerClient.ANDROID_VR.value) success else ExtractionResult.Failure("blocked")
            }
        }
        val service = YoutubePlayerClientFallbackStreamService(
            delegate,
            listOf(YoutubePlayerClient.WEB_SAFARI, YoutubePlayerClient.ANDROID_VR),
        )

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertSame(success, result)
        assertEquals(
            listOf(YoutubePlayerClient.WEB_SAFARI.value, YoutubePlayerClient.ANDROID_VR.value),
            observed,
        )
        assertEquals(YoutubePlayerClient.MWEB.value, NewPipe.getYoutubePlayerClient())
    }

    @Test
    fun `successful classic client stops fallback chain`() = runBlocking {
        val observed = mutableListOf<String>()
        val success = ExtractionResult.Success(testStreamResponse().copy(id = YOUTUBE_URL))
        val delegate = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                observed += NewPipe.getYoutubePlayerClient()
                return success
            }
        }
        val service = YoutubePlayerClientFallbackStreamService(
            delegate,
            listOf(YoutubePlayerClient.WEB_SAFARI, YoutubePlayerClient.ANDROID_VR),
        )

        val result = service.getStreamInfo(YOUTUBE_URL)

        assertSame(success, result)
        assertEquals(listOf(YoutubePlayerClient.WEB_SAFARI.value), observed)
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com/watch?v=GlzleRbo5E0"
    }
}
