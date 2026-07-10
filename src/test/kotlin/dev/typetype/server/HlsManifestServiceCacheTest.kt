package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.OkHttpProxyService
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
            assertEquals(OkHttpProxyService.YOUTUBE_MWEB_USER_AGENT, chain.request().header("User-Agent"))
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
}

private object NoopStreamService : StreamService {
    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        ExtractionResult.Failure("unused")
}

private class InMemoryCache : CacheService {
    private val values = mutableMapOf<String, String>()
    override suspend fun get(key: String): String? = values[key]
    override suspend fun set(key: String, value: String, ttlSeconds: Long) { values[key] = value }
    override suspend fun delete(key: String) { values.remove(key) }
}
