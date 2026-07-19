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
import java.net.URI
import java.util.Base64

class SubscriptionFeedService(
    private val subscriptionsService: SubscriptionsService,
    private val channelService: ChannelService,
    private val cache: CacheService,
) {
    private val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun getFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse {
        val all = cachedAll(userId)
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val nextpage = if (to < all.size) encodeNextPage(page + 1) else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = nextpage)
    }

    suspend fun getAll(userId: String): List<VideoItem> = cachedAll(userId)

    suspend fun getCachedFeed(userId: String, page: Int, limit: Int): SubscriptionFeedResponse? {
        val key = SubscriptionFeedCacheKeys.feed(userId)
        val raw = runCatching { cache.get(key) }.getOrNull() ?: return null
        val all = runCatching {
            CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), raw)
        }.getOrNull() ?: return null
        val from = page * limit
        if (from >= all.size) return SubscriptionFeedResponse(videos = emptyList(), nextpage = null)
        val to = minOf(from + limit, all.size)
        val nextpage = if (to < all.size) encodeNextPage(page + 1) else null
        return SubscriptionFeedResponse(videos = all.subList(from, to), nextpage = nextpage)
    }

    private suspend fun cachedAll(userId: String): List<VideoItem> {
        val key = SubscriptionFeedCacheKeys.feed(userId)
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
                    runCatching { fetchForSubscription(sub.channelUrl) }.getOrElse { emptyList() }
                }
            }.map { it.await() }.flatten()
        }
        val sorted = videos.sortedWith(
            compareByDescending<VideoItem> { it.isLive }
                .thenByDescending { if (it.uploaded == -1L) Long.MIN_VALUE else it.uploaded }
        )
        runCatching { cache.set(key, CacheJson.encodeToString(ListSerializer(VideoItem.serializer()), sorted), FEED_TTL_SECONDS) }
        return sorted
    }

    private suspend fun fetchForSubscription(channelUrl: String): List<VideoItem> = coroutineScope {
        val channel = async { fetchVideos(channelUrl) }
        val livestreams = if (isYoutubeUrl(channelUrl)) {
            async { fetchVideos(channelUrl.toLivestreamsTabUrl()) }
        } else null
        mergeVideos(channel.await(), livestreams?.await().orEmpty())
    }

    private suspend fun fetchVideos(url: String): List<VideoItem> = semaphore.withPermit {
        runCatching {
            withTimeout(CHANNEL_TIMEOUT_MS) {
                (channelService.getChannel(url, null) as? ExtractionResult.Success)?.data?.videos.orEmpty()
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun mergeVideos(channel: List<VideoItem>, livestreams: List<VideoItem>): List<VideoItem> =
        buildMap {
            channel.forEach { put(it.feedKey(), it) }
            livestreams.forEach { put(it.feedKey(), it) }
        }.values.toList()

    private fun VideoItem.feedKey(): String = url.ifBlank { id.ifBlank { "$uploaderUrl|$title" } }

    private fun String.toLivestreamsTabUrl(): String {
        val uri = URI(this)
        val path = uri.path.trimEnd('/')
        val segments = path.split('/').filter(String::isNotBlank)
        val basePath = if (segments.size >= 2 && segments.last() in YOUTUBE_CHANNEL_TABS) {
            path.substringBeforeLast('/')
        } else {
            path
        }
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, "$basePath/streams", null, null).toString()
    }

    private fun encodeNextPage(page: Int): String =
        Base64.getEncoder().encodeToString("""{"page":$page}""".toByteArray())

    companion object {
        private const val FEED_TTL_SECONDS = 60L
        private const val MAX_CONCURRENT_FETCHES = 20
        private const val CHANNEL_TIMEOUT_MS = 15_000L
        private val YOUTUBE_CHANNEL_TABS = setOf("featured", "videos", "shorts", "streams", "playlists", "community", "about")
    }
}
