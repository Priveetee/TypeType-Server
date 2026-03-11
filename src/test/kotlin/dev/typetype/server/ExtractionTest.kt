package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.YouTubeSubtitleService
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.schabi.newpipe.extractor.NewPipe

private object NoOpCache : CacheService {
    override suspend fun get(key: String): String? = null
    override suspend fun set(key: String, value: String, ttlSeconds: Long) = Unit
    override suspend fun delete(key: String) = Unit
}

@Tag("network")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractionTest {

    private val service = PipePipeStreamService(NoOpCache, YouTubeSubtitleService(OkHttpClient()))

    @BeforeAll
    fun setup() {
        NewPipe.init(OkHttpDownloader.instance())
    }

    @Test
    fun `YouTube rickroll has non-empty videoOnlyStreams`() = kotlinx.coroutines.runBlocking {
        val result = service.getStreamInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        assertTrue(data.videoOnlyStreams.isNotEmpty())
    }

    @Test
    fun `YouTube rickroll streams have positive indexStart`() = kotlinx.coroutines.runBlocking {
        val result = service.getStreamInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val data = (result as ExtractionResult.Success).data
        assertTrue(data.videoOnlyStreams.any { it.indexStart > 0 })
    }

    @Test
    fun `YouTube rickroll has non-empty audioStreams`() = kotlinx.coroutines.runBlocking {
        val result = service.getStreamInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val data = (result as ExtractionResult.Success).data
        assertTrue(data.audioStreams.isNotEmpty())
    }

    @Test
    fun `BiliBili video has non-empty videoOnlyStreams`() = kotlinx.coroutines.runBlocking {
        val result = service.getStreamInfo("https://www.bilibili.com/video/BV1xx411c7mD")
        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        assertTrue(data.videoOnlyStreams.isNotEmpty())
    }

    @Test
    fun `NicoNico sm9 has at least one stream`() = kotlinx.coroutines.runBlocking {
        val result = service.getStreamInfo("https://www.nicovideo.jp/watch/sm9")
        assertTrue(result is ExtractionResult.Success)
        val data = (result as ExtractionResult.Success).data
        assertTrue(data.videoStreams.isNotEmpty() || data.videoOnlyStreams.isNotEmpty())
    }
}
