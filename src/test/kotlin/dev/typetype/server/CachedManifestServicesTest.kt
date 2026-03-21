package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.StreamService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedManifestServicesTest {

    @Test
    fun `cached dash manifest serves second request from cache`() = runBlocking {
        val streamService: StreamService = mockk()
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(testStreamResponse())
        val cache = InMemoryCacheService()
        val service = CachedManifestService(ManifestService(streamService), cache)
        val first = service.dashManifest("https://youtube.com/watch?v=test")
        val second = service.dashManifest("https://youtube.com/watch?v=test")
        assertEquals(ExtractionResult.Success::class, first::class)
        assertEquals(ExtractionResult.Success::class, second::class)
        coVerify(exactly = 1) { streamService.getStreamInfo(any()) }
    }

    @Test
    fun `cached native manifest returns cached value without delegate call`() = runBlocking {
        val cache = InMemoryCacheService()
        val delegate: NativeManifestService = mockk()
        coEvery { delegate.nativeManifest(any()) } returns ExtractionResult.Success("<MPD/>")
        val service = CachedNativeManifestService(delegate, cache)
        val url = "https://youtube.com/watch?v=test"
        service.nativeManifest(url)
        service.nativeManifest(url)
        coVerify(exactly = 1) { delegate.nativeManifest(url) }
    }

    private class InMemoryCacheService : CacheService {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun set(key: String, value: String, ttlSeconds: Long) {
            map[key] = value
        }
        override suspend fun delete(key: String) {
            map.remove(key)
        }
    }
}
