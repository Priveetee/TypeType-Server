package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class CachedSuggestionService(
    private val delegate: SuggestionService,
    private val cache: CacheService,
) : SuggestionService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getSuggestions(query: String, serviceId: Int): ExtractionResult<List<String>> {
        val key = "suggestions:$serviceId:$query"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching {
                ExtractionResult.Success(json.decodeFromString(ListSerializer(String.serializer()), cached))
            }.getOrElse { delegate.getSuggestions(query, serviceId) }
        }
        val result = delegate.getSuggestions(query, serviceId)
        if (result is ExtractionResult.Success) {
            runCatching {
                cache.set(key, json.encodeToString(ListSerializer(String.serializer()), result.data), SUGGESTIONS_TTL_SECONDS)
            }
        }
        return result
    }

    private companion object {
        const val SUGGESTIONS_TTL_SECONDS = 60L
    }
}
