package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.HomeRecommendationsResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    suspend fun getHome(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
    ): HomeRecommendationsResponse {
        val pool = cachedPool(userId, serviceId)
        val page = HomeRecommendationMixer.mix(pool = pool, cursor = cursor, limit = limit)
        return HomeRecommendationsResponse(
            items = page.items,
            nextCursor = page.nextCursor,
            hasMore = page.nextCursor != null,
        )
    }

    private suspend fun cachedPool(userId: String, serviceId: Int): HomeRecommendationPool {
        val key = cacheKey(userId, serviceId)
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
                ?.let { return it }
        }
        val built = HomeRecommendationBuilder(
            subscriptionsService = subscriptionsService,
            subscriptionFeedService = subscriptionFeedService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
            trendingService = trendingService,
        ).build(userId = userId, serviceId = serviceId)
        runCatching { cache.set(key, CacheJson.encodeToString(built), CACHE_TTL_SECONDS) }
        return built
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
