package dev.typetype.server.services

class HomeRecommendationSignalContextService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService = FavoritesService(),
) {
    suspend fun load(userId: String): HomeRecommendationSignalContext {
        val subscriptions = subscriptionsService.getAll(userId)
        val history = historyService.search(
            userId = userId,
            q = null,
            from = null,
            to = null,
            limit = 60,
            offset = 0,
        ).first
        val favorites = favoritesService.getAll(userId)
        return HomeRecommendationSignalContext(
            userSubscriptions = subscriptions.map { it.channelUrl },
            historyItems = history.map { it.url },
            favoriteUrls = favorites.map { it.videoUrl },
        )
    }
}
