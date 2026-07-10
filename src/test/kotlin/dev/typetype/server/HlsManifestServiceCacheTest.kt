package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.StreamService
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HlsManifestServiceCacheTest {
    @Test
    fun `hls manifests are cached briefly by manifest url`() = runTest {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            calls += 1
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U\nsegment.ts".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        }).build()
        val service = HlsManifestService(NoopStreamService, client, InMemoryCache())
        val url = "https://example.com/master.m3u8"

        service.hlsManifest(url)
        service.hlsManifest(url)

        assertEquals(1, calls)
    }

    @Test
    fun `attested manifest is scoped to youtube live`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val attestedVideoIds = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requestedUrls += chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("#EXTM3U".toResponseBody("application/vnd.apple.mpegurl".toMediaType()))
                .build()
        }).build()
        val streams = FixedStreamService(
            testStreamResponse(hlsUrl = "https://example.com/legacy.m3u8").copy(isLive = true),
        )
        val service = HlsManifestService(streams, client, attestedYoutubeHls = { videoId ->
            attestedVideoIds += videoId
            "https://example.com/attested.m3u8"
        })

        val publicResult = service.hlsManifest("https://youtube.com/watch?v=test-id")
        val sessionResult = service.hlsManifestFromStreamInfo(
            ExtractionResult.Success(testStreamResponse().copy(id = "session-id", isLive = true)),
        )
        val bilibiliResult = service.hlsManifest("https://www.bilibili.com/video/BV1xx411c7mD")

        assertEquals(ExtractionResult.Success("#EXTM3U"), publicResult)
        assertEquals(ExtractionResult.Success("#EXTM3U"), sessionResult)
        assertEquals(ExtractionResult.Success("#EXTM3U"), bilibiliResult)
        assertEquals(listOf("test-id", "session-id"), attestedVideoIds)
        assertEquals(
            listOf(
                "https://example.com/attested.m3u8",
                "https://example.com/attested.m3u8",
                "https://example.com/legacy.m3u8",
            ),
            requestedUrls,
        )
    }
}

private object NoopStreamService : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        ExtractionResult.Failure("unused")
}

private class FixedStreamService(private val response: StreamResponse) : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = ExtractionResult.Success(response)
}

private class InMemoryCache : CacheService {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun set(key: String, value: String, ttlSeconds: Long) { values[key] = value }
    override suspend fun delete(key: String) { values.remove(key) }
}
