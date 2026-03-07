package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.json.Json

class CachedChannelService(
    private val delegate: ChannelService,
    private val cache: CacheService,
) : ChannelService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getChannel(url: String, nextpage: String?): ExtractionResult<ChannelResponse> {
        val key = "channel:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(json.decodeFromString<ChannelResponse>(cached)) }.getOrElse {
                delegate.getChannel(url, nextpage)
            }
        }
        val result = delegate.getChannel(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, json.encodeToString(ChannelResponse.serializer(), result.data), 600L) }
        }
        return result
    }
}
