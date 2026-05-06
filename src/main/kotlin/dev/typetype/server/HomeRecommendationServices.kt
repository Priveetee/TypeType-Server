package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.HomeRecommendationPoolResolver
import dev.typetype.server.services.HomeRecommendationPoolResolverDependencies
import dev.typetype.server.services.HomeRecommendationService

data class HomeRecommendationServices(
    val recommendationService: HomeRecommendationService,
)

fun createHomeRecommendationServices(
    cache: DragonflyService,
    deps: HomeRecommendationPoolResolverDependencies,
): HomeRecommendationServices {
    val resolverDeps = deps.copy(cache = cache)
    val recommendationService = HomeRecommendationService(
        poolResolver = HomeRecommendationPoolResolver(resolverDeps),
    )
    return HomeRecommendationServices(recommendationService)
}
