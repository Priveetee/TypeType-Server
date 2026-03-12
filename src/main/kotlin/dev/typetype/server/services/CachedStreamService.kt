package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class CachedStreamService(
    private val delegate: StreamService,
    private val cache: CacheService,
) : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val key = "stream:$url"
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

    private companion object {
        const val STREAM_TTL_SECONDS = 14400L
    }
}

