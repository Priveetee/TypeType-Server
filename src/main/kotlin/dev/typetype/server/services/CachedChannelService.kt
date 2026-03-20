package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class CachedChannelService(
    private val delegate: ChannelService,
    private val cache: CacheService,
) : ChannelService {

    companion object {
        private const val CHANNEL_CACHE_TTL_SECONDS = 1800L
    }

    override suspend fun getChannel(url: String, nextpage: String?): ExtractionResult<ChannelResponse> {
        val key = "channel:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<ChannelResponse>(cached)) }.getOrElse {
                delegate.getChannel(url, nextpage)
            }
        }
        val result = delegate.getChannel(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(ChannelResponse.serializer(), result.data), CHANNEL_CACHE_TTL_SECONDS) }
        }
        return result
    }
}
