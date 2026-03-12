package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import java.util.Base64

class SubscriptionFeedService(
    private val subscriptionsService: SubscriptionsService,
    private val channelService: ChannelService,
    private val cache: CacheService,
) {
    private val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun getFeed(token: String, page: Int, limit: Int): SubscriptionFeedResponse {
        val all = cachedAll(token)
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val nextpage = if (to < all.size) encodeNextPage(page + 1) else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = nextpage)
    }

    private suspend fun cachedAll(token: String): List<VideoItem> {
        val key = "feed:$token"
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            return runCatching { CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw) }
                .getOrElse { fetchAndCache(key) }
        }
        return fetchAndCache(key)
    }

    private suspend fun fetchAndCache(key: String): List<VideoItem> {
        val subs = subscriptionsService.getAll()
        val videos = coroutineScope {
            subs.map { sub ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            withTimeout(CHANNEL_TIMEOUT_MS) {
                                (channelService.getChannel(sub.channelUrl, null) as? ExtractionResult.Success)
                                    ?.data?.videos.orEmpty()
                            }
                        }.getOrElse { emptyList() }
                    }
                }
            }.map { it.await() }.flatten()
        }
        val sorted = videos.sortedWith(
            compareByDescending { v: VideoItem -> if (v.uploaded == -1L) Long.MIN_VALUE else v.uploaded }
        )
        runCatching { cache.set(key, CacheJson.encodeToString(ListSerializer(VideoItem.serializer()), sorted), FEED_TTL_SECONDS) }
        return sorted
    }

    private fun encodeNextPage(page: Int): String =
        Base64.getEncoder().encodeToString("""{"page":$page}""".toByteArray())

    companion object {
        private const val FEED_TTL_SECONDS = 300L
        private const val MAX_CONCURRENT_FETCHES = 20
        private const val CHANNEL_TIMEOUT_MS = 15_000L
    }
}
