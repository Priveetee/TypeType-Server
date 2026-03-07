package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.serialization.json.Json

class CachedStreamService(
    private val delegate: StreamService,
    private val cache: CacheService,
) : StreamService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
        val key = "stream:$url"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(json.decodeFromString<StreamResponse>(cached)) }.getOrElse {
                delegate.getStreamInfo(url)
            }
        }
        val result = delegate.getStreamInfo(url)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, json.encodeToString(StreamResponse.serializer(), result.data), 21600L) }
        }
        return result
    }
}
