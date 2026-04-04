package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.HomeRecommendationPool
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

class HomeRecommendationPoolResolver(
    private val subscriptionsService: SubscriptionsService,
    private val subscriptionFeedService: SubscriptionFeedService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val feedbackService: RecommendationFeedbackService,
    private val eventService: RecommendationEventService,
    private val feedHistoryService: RecommendationFeedHistoryService,
    private val trendingService: TrendingService,
    private val searchService: SearchService,
    private val cache: CacheService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fullBuilds = ConcurrentHashMap<String, Deferred<HomeRecommendationPool>>()
    private val pendingPersistence = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(userId: String, serviceId: Int, personalizationEnabled: Boolean): HomeRecommendationPool {
        val key = cacheKey(userId = userId, serviceId = serviceId, personalizationEnabled = personalizationEnabled)
        val cached = readCachedPool(key)
        if (cached != null) return cached
        val fullBuild = fullBuild(key, userId, serviceId, personalizationEnabled)
        val quickFull = withTimeoutOrNull(FULL_BUILD_BUDGET_MS) { fullBuild.await() }
        if (quickFull != null) {
            writeCachedPool(key, quickFull)
            return quickFull
        }
        schedulePersistence(key, fullBuild)
        return buildPool(userId, serviceId, HomeRecommendationPoolMode.FAST, personalizationEnabled)
    }

    private suspend fun readCachedPool(key: String): HomeRecommendationPool? {
        runCatching { cache.get(key) }.getOrNull()?.let { raw ->
            return runCatching { CacheJson.decodeFromString<HomeRecommendationPool>(raw) }.getOrNull()
        }
        return null
    }

    private fun fullBuild(
        key: String,
        userId: String,
        serviceId: Int,
        personalizationEnabled: Boolean,
    ): Deferred<HomeRecommendationPool> {
        fullBuilds[key]?.let { return it }
        val created = scope.async { buildPool(userId, serviceId, HomeRecommendationPoolMode.FULL, personalizationEnabled) }
        val winner = fullBuilds.putIfAbsent(key, created)
        if (winner != null) {
            created.cancel()
            return winner
        }
        created.invokeOnCompletion { fullBuilds.remove(key, created) }
        return created
    }

    private suspend fun buildPool(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        personalizationEnabled: Boolean,
    ): HomeRecommendationPool = HomeRecommendationBuilder(
        subscriptionsService = subscriptionsService,
        subscriptionFeedService = subscriptionFeedService,
        historyService = historyService,
        favoritesService = favoritesService,
        watchLaterService = watchLaterService,
        blockedService = blockedService,
        eventService = eventService,
        feedbackService = feedbackService,
        feedHistoryService = feedHistoryService,
        trendingService = trendingService,
        searchService = searchService,
    ).build(userId = userId, serviceId = serviceId, mode = mode, personalizationEnabled = personalizationEnabled)

    private fun schedulePersistence(key: String, build: Deferred<HomeRecommendationPool>) {
        if (!pendingPersistence.add(key)) return
        scope.launch {
            runCatching { build.await() }.getOrNull()?.let { writeCachedPool(key, it) }
            pendingPersistence.remove(key)
        }
    }

    private suspend fun writeCachedPool(key: String, pool: HomeRecommendationPool): Unit =
        runCatching { cache.set(key, CacheJson.encodeToString(pool), CACHE_TTL_SECONDS) }.let { }

    private fun cacheKey(userId: String, serviceId: Int, personalizationEnabled: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val seed = "$CACHE_VERSION:$userId:$serviceId:$personalizationEnabled"
        val hex = digest.digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
        return "recommendations:home:$hex"
    }

    companion object {
        private const val CACHE_TTL_SECONDS = 300L
        private const val FULL_BUILD_BUDGET_MS = 1_500L
        private const val CACHE_VERSION = 6
    }
}
