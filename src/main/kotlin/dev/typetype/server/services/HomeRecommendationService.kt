package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.HomeRecommendationsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class HomeRecommendationService(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val feedbackService: RecommendationFeedbackService,
    private val eventService: RecommendationEventService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
    private val cache: CacheService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fullBuilds = ConcurrentHashMap<String, Deferred<HomeRecommendationPool>>()
    private val pendingPersistence = ConcurrentHashMap.newKeySet<String>()
    suspend fun getHome(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
    ): HomeRecommendationsResponse {
        val key = cacheKey(userId, serviceId)
        val cached = readCachedPool(key)
        val pool = if (cached != null) {
            cached
        } else {
            val fullBuild = fullBuild(key, userId, serviceId)
            val quickFull = withTimeoutOrNull(FULL_BUILD_BUDGET_MS) { fullBuild.await() }
            if (quickFull != null) {
                writeCachedPool(key, quickFull)
                quickFull
            } else {
                schedulePersistence(key, fullBuild)
                buildPool(userId, serviceId, HomeRecommendationPoolMode.FAST)
            }
        }
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = cursor,
            limit = limit,
            sourceWeights = HomeRecommendationExploreBonus.apply(
                sourceWeights = pool.sourceWeights,
                pageIndex = HomeRecommendationCursorPageIndex.from(cursor, limit),
            ),
        )
        return HomeRecommendationsResponse(
            items = page.items,
            nextCursor = page.nextCursor,
            hasMore = page.nextCursor != null,
        )
    }
    private suspend fun readCachedPool(key: String): HomeRecommendationPool? {
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            return runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
        }
        return null
    }
    private fun fullBuild(key: String, userId: String, serviceId: Int): Deferred<HomeRecommendationPool> {
        fullBuilds[key]?.let { return it }
        val created = scope.async { buildPool(userId, serviceId, HomeRecommendationPoolMode.FULL) }
        val winner = fullBuilds.putIfAbsent(key, created)
        if (winner != null) {
            created.cancel()
            return winner
        }
        created.invokeOnCompletion { fullBuilds.remove(key, created) }
        return created
    }
    private suspend fun buildPool(userId: String, serviceId: Int, mode: HomeRecommendationPoolMode): HomeRecommendationPool =
        HomeRecommendationBuilder(
            subscriptionsService = subscriptionsService,
            subscriptionFeedService = subscriptionFeedService,
            historyService = historyService,
            favoritesService = favoritesService,
            watchLaterService = watchLaterService,
            blockedService = blockedService,
            eventService = eventService,
            feedbackService = feedbackService,
            trendingService = trendingService,
            searchService = searchService,
        ).build(userId = userId, serviceId = serviceId, mode = mode)
    private fun schedulePersistence(key: String, build: Deferred<HomeRecommendationPool>) {
        if (!pendingPersistence.add(key)) return
        scope.launch {
            runCatching { build.await() }.getOrNull()?.let { writeCachedPool(key, it) }
            pendingPersistence.remove(key)
        }
    }
    private suspend fun writeCachedPool(key: String, pool: HomeRecommendationPool): Unit =
        runCatching { cache.set(key, CacheJson.encodeToString(pool), CACHE_TTL_SECONDS) }.let { }
    private fun cacheKey(userId: String, serviceId: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hex = digest.digest("$CACHE_VERSION:$userId:$serviceId".toByteArray()).joinToString("") { "%02x".format(it) }
        return "recommendations:home:$hex"
    }
    companion object {
        private const val CACHE_TTL_SECONDS = 300L
        private const val FULL_BUILD_BUDGET_MS = 1_500L
        private const val CACHE_VERSION = 5
    }
}
