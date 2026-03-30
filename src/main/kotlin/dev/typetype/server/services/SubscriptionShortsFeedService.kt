package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import java.security.MessageDigest

class SubscriptionShortsFeedService(
    private val subscriptionsService: SubscriptionsService,
    private val channelService: ChannelService,
    private val cache: CacheService,
) {
    private val semaphore = Semaphore(20)

    suspend fun getFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse {
        val all = cachedAll(userId)
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val next = if (to < all.size) (page + 1).toString() else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = next)
    }

    private suspend fun cachedAll(userId: String): List<VideoItem> {
        val key = cacheKey(userId)
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            return runCatching { CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw) }
                .getOrElse { fetchAndCache(userId, key) }
        }
        return fetchAndCache(userId, key)
    }

    private suspend fun fetchAndCache(userId: String, key: String): List<VideoItem> {
        val subs = subscriptionsService.getAll(userId)
        val videos = coroutineScope {
            subs.map { sub ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            withTimeout(15_000L) {
                                channelService.getChannel(sub.channelUrl, null)
                            }
                        }.getOrNull()?.let { result ->
                            when (result) {
                                is dev.typetype.server.models.ExtractionResult.Success -> result.data.videos
                                else -> emptyList()
                            }
                        }.orEmpty()
                    }
                }
            }.map { it.await() }.flatten()
        }
        val dedup = videos.distinctBy { it.url }
            .filter { it.isShortFormContent }
            .sortedByDescending { if (it.uploaded == -1L) Long.MIN_VALUE else it.uploaded }
        runCatching { cache.set(key, CacheJson.encodeToString(ListSerializer(VideoItem.serializer()), dedup), 300L) }
        return dedup
    }

    private fun cacheKey(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hex = digest.digest(userId.toByteArray()).joinToString("") { "%02x".format(it) }
        return "feed:shorts:$hex"
    }
}
