package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse

class CachedSearchService(
    private val delegate: SearchService,
    private val cache: CacheService,
) : SearchService {

    override suspend fun search(
        query: String,
        serviceId: Int,
        nextpage: String?,
    ): ExtractionResult<SearchPageResponse> {
        val key = "search:$serviceId:$query:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<SearchPageResponse>(cached)) }.getOrElse {
                delegate.search(query, serviceId, nextpage)
            }
        }
        val result = delegate.search(query, serviceId, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(SearchPageResponse.serializer(), result.data), 300L) }
        }
        return result
    }
}

