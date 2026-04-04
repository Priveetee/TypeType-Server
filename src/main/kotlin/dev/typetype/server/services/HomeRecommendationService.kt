package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsResponse

class HomeRecommendationService(
    private val poolResolver: HomeRecommendationPoolResolver,
    private val feedHistoryService: RecommendationFeedHistoryService,
    private val privacyService: RecommendationPrivacyService,
) {
    suspend fun getHome(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
    ): HomeRecommendationsResponse {
        val personalizationEnabled = privacyService.isPersonalizationEnabled(userId)
        val pool = poolResolver.resolve(userId = userId, serviceId = serviceId, personalizationEnabled = personalizationEnabled)
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = cursor,
            limit = limit,
            sourceWeights = HomeRecommendationExploreBonus.apply(
                sourceWeights = pool.sourceWeights,
                pageIndex = HomeRecommendationCursorPageIndex.from(cursor, limit),
            ),
        )
        if (personalizationEnabled) {
            feedHistoryService.recordShown(userId, page.items.map { it.url })
        }
        return HomeRecommendationsResponse(
            items = page.items,
            nextCursor = page.nextCursor,
            hasMore = page.nextCursor != null,
        )
    }
}
