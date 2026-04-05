package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsDebug
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
        if (personalizationEnabled) {
            feedHistoryService.recordShown(args.userId, page.items.map { it.url })
        }
        return HomeRecommendationsResponse(
            items = page.items,
            nextCursor = page.nextCursor,
            hasMore = page.nextCursor != null,
            debug = if (args.debug) {
                val itemSources = page.items.associate { item ->
                    val source = page.sourceByUrl[item.url] ?: HomeRecommendationSourceTag.DISCOVERY_EXPLORATION
                    item.url to source.apiLabel()
                }
                val sourceBreakdown = HomeRecommendationSourceTag.entries.associate { tag ->
                    tag.apiLabel() to itemSources.values.count { it == tag.apiLabel() }
                }
                val total = page.items.size.coerceAtLeast(1)
                HomeRecommendationsDebug(
                    itemSources = itemSources,
                    sourceBreakdown = sourceBreakdown,
                    discoveryRatio = page.discoveryCount.toDouble() / total.toDouble(),
                    targetDiscoveryRatio = page.targetDiscoveryRatio,
                    discoveryFloorRatio = page.discoveryFloorRatio,
                )
            } else {
                null
            },
        )
    }
}
