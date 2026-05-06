package dev.typetype.server.services

class HomeRecommendationUserSignalService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
) {
    suspend fun loadProfile(userId: String): HomeRecommendationProfile {
        val subscriptions = subscriptionsService.getAll(userId)
        val favorites = favoritesService.getAll(userId)
        val watchLater = watchLaterService.getAll(userId)
        val historyItems = historyService.search(userId = userId, q = null, from = null, to = null, limit = 240, offset = 0).first
        val blockedVideos = blockedService.getVideos(userId).map { it.url }.toSet()
        val blockedChannels = blockedService.getChannels(userId).map { it.url }.toSet()
        val seenUrls = historyItems.map { it.url }.toSet()
        val favoriteUrls = favorites.map { it.videoUrl }.toSet()
        val watchLaterUrls = watchLater.map { it.url }.toSet()
        val subscriptionChannels = subscriptions.map { it.channelUrl }.toSet()
        return HomeRecommendationProfile(
            seenUrls = seenUrls,
            blockedVideos = blockedVideos,
            blockedChannels = blockedChannels,
            feedbackBlockedVideos = emptySet(),
            feedbackBlockedChannels = emptySet(),
            subscriptionChannels = subscriptionChannels,
            favoriteUrls = favoriteUrls,
            watchLaterUrls = watchLaterUrls,
            keywordAffinity = emptySet(),
            themeTokens = emptySet(),
            themeQueries = emptyList(),
            personalizationEnabled = false,
        )
    }
}
