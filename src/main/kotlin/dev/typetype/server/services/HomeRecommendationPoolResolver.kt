package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeRecommendationPoolResolver(
    private val dependencies: HomeRecommendationPoolResolverDependencies,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = HomeRecommendationPoolResolverState()
    private val poolCache = HomeRecommendationPoolCache(dependencies.cache)

    suspend fun resolve(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        personalizationEnabled: Boolean,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool {
        val key = poolCache.key(
            userId = userId,
            serviceId = serviceId,
            mode = mode,
            personalizationEnabled = personalizationEnabled,
        )
        val cached = poolCache.read(key)
        if (cached != null) return cached
        val fullBuild = fullBuild(key, userId, serviceId, mode, personalizationEnabled, context)
        val quickFull = withTimeoutOrNull(FULL_BUILD_BUDGET_MS) { fullBuild.await() }
        if (quickFull != null) {
            poolCache.write(key, quickFull)
            return quickFull
        }
        schedulePersistence(key, fullBuild)
        val fastMode = if (mode == HomeRecommendationPoolMode.SHORTS) HomeRecommendationPoolMode.SHORTS else HomeRecommendationPoolMode.FAST
        return buildPool(userId, serviceId, fastMode, personalizationEnabled, context)
    }

    private fun fullBuild(
        key: String,
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        personalizationEnabled: Boolean,
        context: HomeRecommendationContext,
    ): Deferred<HomeRecommendationPool> {
        state.fullBuilds[key]?.let { return it }
        val created = scope.async {
            val fullMode = if (mode == HomeRecommendationPoolMode.SHORTS) HomeRecommendationPoolMode.SHORTS else HomeRecommendationPoolMode.FULL
            buildPool(userId, serviceId, fullMode, personalizationEnabled, context)
        }
        val winner = state.fullBuilds.putIfAbsent(key, created)
        if (winner != null) {
            created.cancel()
            return winner
        }
        created.invokeOnCompletion { state.fullBuilds.remove(key, created) }
        return created
    }

    private suspend fun buildPool(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        personalizationEnabled: Boolean,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool = HomeRecommendationBuilder(
        subscriptionsService = dependencies.subscriptionsService,
        subscriptionFeedService = dependencies.subscriptionFeedService,
        historyService = dependencies.historyService,
        playlistService = dependencies.playlistService,
        favoritesService = dependencies.favoritesService,
        watchLaterService = dependencies.watchLaterService,
        blockedService = dependencies.blockedService,
        eventService = dependencies.eventService,
        feedbackService = dependencies.feedbackService,
        feedHistoryService = dependencies.feedHistoryService,
        trendingService = dependencies.trendingService,
        searchService = dependencies.searchService,
        signalContextService = dependencies.signalContextService,
    ).build(
        userId = userId,
        serviceId = serviceId,
        mode = mode,
        personalizationEnabled = personalizationEnabled,
        context = context,
    )

    private fun schedulePersistence(key: String, build: Deferred<HomeRecommendationPool>) {
        if (!state.pendingPersistence.add(key)) return
        scope.launch {
            runCatching { build.await() }.getOrNull()?.let { poolCache.write(key, it) }
            state.pendingPersistence.remove(key)
        }
    }

    companion object {
        private const val FULL_BUILD_BUDGET_MS = 1_500L
    }
}
