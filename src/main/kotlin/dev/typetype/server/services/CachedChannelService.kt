package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult

class CachedChannelService(
    private val delegate: ChannelService,
    private val cache: CacheService,
) : ChannelService {

    override suspend fun getChannel(url: String, nextpage: String?, sort: String?): ExtractionResult<ChannelResponse> =
        PublicExtractionCache.getOrLoad(
            cache = cache,
            area = "channel",
            key = PublicCacheKey.of("channel", url, nextpage, sort),
            serializer = ChannelResponse.serializer(),
            ttlSeconds = { PublicCachePolicy.channelTtl(url, nextpage, sort) },
        ) { delegate.getChannel(url, nextpage, sort) }
}
