package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsResponse

object HomeRecommendationPageBuilder {
    suspend fun build(
        args: HomeRecommendationApiArgs,
        mode: HomeRecommendationPoolMode,
        poolResolver: HomeRecommendationPoolResolver,
        feedHistoryService: RecommendationFeedHistoryService,
        privacyService: RecommendationPrivacyService,
    ): HomeRecommendationsResponse {
        val personalizationEnabled = privacyService.isPersonalizationEnabled(args.userId)
        val pool = poolResolver.resolve(
            userId = args.userId,
            serviceId = args.serviceId,
            mode = mode,
            personalizationEnabled = personalizationEnabled,
            context = args.context,
        )
        val page = HomeRecommendationMixer.mix(
            pool = pool,
            cursor = args.cursor,
            limit = args.limit,
            context = args.context.sessionContext,
            sourceWeights = HomeRecommendationExploreBonus.apply(
                sourceWeights = pool.sourceWeights,
                pageIndex = HomeRecommendationCursorPageIndex.from(args.cursor, args.limit),
            ),
            mode = mode,
            userId = args.userId,
            serviceId = args.serviceId,
        )
        val finalPage = if (mode == HomeRecommendationPoolMode.SHORTS) {
            val refresh = HomeRecommendationShortsRefresher.refresh(
                pool = pool,
                page = page,
                cursor = args.cursor,
            )
            if (refresh.pool == pool) {
                page
            } else {
                HomeRecommendationMixer.mix(
                    pool = refresh.pool,
                    cursor = refresh.cursorOverride ?: args.cursor,
                    limit = args.limit,
                    context = args.context.sessionContext,
                    sourceWeights = HomeRecommendationExploreBonus.apply(
                        sourceWeights = refresh.pool.sourceWeights,
                        pageIndex = HomeRecommendationCursorPageIndex.from(refresh.cursorOverride ?: args.cursor, args.limit),
                    ),
                    mode = mode,
                    userId = args.userId,
                    serviceId = args.serviceId,
                )
            }
        } else {
            page
        }
        if (personalizationEnabled) {
            feedHistoryService.recordShown(args.userId, finalPage.items.map { it.url })
        }
        return HomeRecommendationsResponse(
            items = finalPage.items,
            nextCursor = finalPage.nextCursor,
            hasMore = finalPage.nextCursor != null,
            debug = if (args.debug) {
                HomeRecommendationShortsDebugInfo.fromPage(finalPage)
            } else {
                null
            },
        )
    }
}
