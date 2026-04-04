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
        context: HomeRecommendationContext,
    ): HomeRecommendationsResponse = HomeRecommendationPageBuilder.build(
        args = HomeRecommendationApiArgs(
            userId = userId,
            serviceId = serviceId,
            limit = limit,
            cursor = cursor,
            context = context,
        ),
        mode = HomeRecommendationPoolMode.FULL,
        poolResolver = poolResolver,
        feedHistoryService = feedHistoryService,
        privacyService = privacyService,
    )

    suspend fun getShorts(
        userId: String,
        serviceId: Int,
        limit: Int,
        cursor: HomeRecommendationCursor,
        context: HomeRecommendationContext,
    ): HomeRecommendationsResponse = HomeRecommendationPageBuilder.build(
        args = HomeRecommendationApiArgs(
            userId = userId,
            serviceId = serviceId,
            limit = limit,
            cursor = cursor,
            context = context,
        ),
        mode = HomeRecommendationPoolMode.SHORTS,
        poolResolver = poolResolver,
        feedHistoryService = feedHistoryService,
        privacyService = privacyService,
    )
}
