package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class CachedTrendingService(
    private val delegate: TrendingService,
    private val cache: CacheService,
) : TrendingService {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(VideoItem.serializer())

    override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> {
        val key = "trending:$serviceId"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(json.decodeFromString(listSerializer, cached)) }.getOrElse {
                delegate.getTrending(serviceId)
            }
        }
        val result = delegate.getTrending(serviceId)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, json.encodeToString(listSerializer, result.data), 900L) }
        }
        return result
    }
}
