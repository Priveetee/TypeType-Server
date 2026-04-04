package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.RecommendationFeedHistoryService
import dev.typetype.server.services.RecommendationPrivacyService

data class HomeRecommendationServices(
    val feedHistoryService: RecommendationFeedHistoryService,
    val recommendationService: HomeRecommendationService,
)

fun createHomeRecommendationServices(
    cache: DragonflyService,
    deps: HomeRecommendationPoolResolverDependencies,
    privacyService: RecommendationPrivacyService,
): HomeRecommendationServices {
    val feedHistoryService = RecommendationFeedHistoryService()
    val resolverDeps = deps.copy(cache = cache, feedHistoryService = feedHistoryService)
    val recommendationService = HomeRecommendationService(
        poolResolver = HomeRecommendationPoolResolver(resolverDeps),
        feedHistoryService = feedHistoryService,
        privacyService = privacyService,
    )
    return HomeRecommendationServices(feedHistoryService, recommendationService)
}
