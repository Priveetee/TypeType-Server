package dev.typetype.server.services

class HomeRecommendationSignalContextService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
) {
    suspend fun load(userId: String): HomeRecommendationSignalContext {
        val subscriptions = subscriptionsService.getAll(userId).map { it.channelUrl }
        val history = historyService.search(
            userId = userId,
            q = null,
            from = null,
            to = null,
            limit = 200,
            offset = 0,
        ).first.map { it.url }
        return HomeRecommendationSignalContext(
            userSubscriptions = subscriptions,
            historyItems = history,
        )
    }
}
