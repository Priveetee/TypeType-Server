package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class CachedStreamService(
    private val delegate: StreamService,
    private val cache: CacheService,
) : StreamService {

    companion object {
        private const val STREAM_TTL_SECONDS = 21600L
        fun cacheKey(url: String): String = "stream:$url"
    }

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val key = cacheKey(url)
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<StreamResponse>(cached)) }.getOrElse {
                delegate.getStreamInfo(url)
            }
        }
        val result = delegate.getStreamInfo(url)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(StreamResponse.serializer(), result.data), STREAM_TTL_SECONDS) }
        }
        return result
    }

}
