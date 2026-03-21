package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.builtins.ListSerializer
import java.security.MessageDigest

class HomeRecommendationService(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val trendingService: TrendingService,
    private val cache: CacheService,
) {
    suspend fun getHome(userId: String, serviceId: Int, limit: Int, index: Int): HomeRecommendationsResponse {
        val items = cachedRecommendations(userId, serviceId)
        if (index >= items.size) return HomeRecommendationsResponse(items = emptyList(), nextCursor = null, hasMore = false)
        val end = minOf(index + limit, items.size)
        val nextCursor = if (end < items.size) HomeRecommendationCursorCodec.encode(HomeRecommendationCursor(index = end)) else null
        return HomeRecommendationsResponse(items = items.subList(index, end), nextCursor = nextCursor, hasMore = end < items.size)
    }

    private suspend fun cachedRecommendations(userId: String, serviceId: Int): List<VideoItem> {
        val key = cacheKey(userId, serviceId)
        val cached = runCatching { cache.get(key) }.getOrNull()
        if (cached != null) {
            val decoded = runCatching { CacheJson.decodeFromString(ListSerializer(VideoItem.serializer()), cached) }.getOrNull()
            if (decoded != null) return decoded
        }
        val built = buildRecommendations(userId, serviceId)
        runCatching { cache.set(key, CacheJson.encodeToString(ListSerializer(VideoItem.serializer()), built), CACHE_TTL_SECONDS) }
        return built
    }

    private suspend fun buildRecommendations(userId: String, serviceId: Int): List<VideoItem> {
        val blockedVideos = blockedService.getVideos(userId).asSequence().map { it.url }.toSet()
        val blockedChannels = blockedService.getChannels(userId).asSequence().map { it.url }.toSet()
        val history = historyService.search(userId = userId, q = null, from = null, to = null, limit = 200, offset = 0).first
        val seenUrls = history.asSequence().map { it.url }.toSet()
        val affinity = history.filter { it.channelUrl.isNotBlank() }.groupingBy { it.channelUrl }.eachCount()
        val subscriptions = subscriptionsService.getAll(userId)
        val subChannels = subscriptions.asSequence().map { it.channelUrl }.toSet()
        val favorites = favoritesService.getAll(userId).asSequence().map { it.videoUrl }.toSet()
        val watchLater = watchLaterService.getAll(userId).asSequence().map { it.url }.toSet()
        val feed = subscriptionFeedService.getFeed(userId = userId, page = 0, limit = 180).videos
        val trending = when (val result = trendingService.getTrending(serviceId)) {
            is ExtractionResult.Success -> result.data
            else -> emptyList()
        }
        val scored = linkedMapOf<String, Pair<VideoItem, Double>>()
        ingest(scored, feed, 1.3, blockedVideos, blockedChannels, seenUrls, subChannels, favorites, watchLater, affinity)
        ingest(scored, trending, 1.0, blockedVideos, blockedChannels, seenUrls, subChannels, favorites, watchLater, affinity)
        return scored.values.sortedWith(compareByDescending<Pair<VideoItem, Double>> { it.second }.thenByDescending { it.first.uploaded }.thenBy { it.first.url })
            .map { it.first }
    }

    private fun ingest(
        target: MutableMap<String, Pair<VideoItem, Double>>, items: List<VideoItem>, base: Double, blockedVideos: Set<String>, blockedChannels: Set<String>,
        seenUrls: Set<String>, subChannels: Set<String>, favorites: Set<String>, watchLater: Set<String>, affinity: Map<String, Int>
    ): Unit {
        items.forEach { item ->
            if (item.url.isBlank() || item.url in blockedVideos || item.url in seenUrls) return@forEach
            if (item.uploaderUrl.isNotBlank() && item.uploaderUrl in blockedChannels) return@forEach
            val affinityBoost = (affinity[item.uploaderUrl] ?: 0).coerceAtMost(4) * 0.15
            val recencyBoost = if (item.uploaded > 0L) recency(item.uploaded) else 0.0
            val score = base + recencyBoost + affinityBoost + if (item.uploaderUrl in subChannels) 0.35 else 0.0 + if (item.url in favorites) 0.2 else 0.0 + if (item.url in watchLater) 0.1 else 0.0
            val current = target[item.url]
            if (current == null || score > current.second) target[item.url] = item to score
        }
    }

    private fun recency(uploaded: Long): Double {
        val ageHours = (System.currentTimeMillis() - uploaded).coerceAtLeast(0L) / 3_600_000.0
        return 1.0 / (1.0 + ageHours / 72.0)
    }

    private fun cacheKey(userId: String, serviceId: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hex = digest.digest("$userId:$serviceId".toByteArray()).joinToString("") { "%02x".format(it) }
        return "recommendations:home:$hex"
    }

    companion object {
        private const val CACHE_TTL_SECONDS = 300L
    }
}
