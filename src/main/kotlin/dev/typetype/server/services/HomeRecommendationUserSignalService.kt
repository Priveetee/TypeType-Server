package dev.typetype.server.services

class HomeRecommendationUserSignalService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val feedbackSignalService: RecommendationFeedbackSignalService,
) {
    suspend fun loadProfile(userId: String): HomeRecommendationProfile {
        val subscriptions = subscriptionsService.getAll(userId)
        val favorites = favoritesService.getAll(userId)
        val watchLater = watchLaterService.getAll(userId)
        val historyItems = historyService.search(
            userId = userId,
            q = null,
            from = null,
            to = null,
            limit = 240,
            offset = 0,
        ).first
        val blockedVideos = blockedService.getVideos(userId).map { it.url }.toSet()
        val blockedChannels = blockedService.getChannels(userId).map { it.url }.toSet()
        val feedbackSignals = feedbackSignalService.load(userId)
        val seenUrls = historyItems.map { it.url }.toSet()
        val favoriteUrls = favorites.map { it.videoUrl }.toSet()
        val watchLaterUrls = watchLater.map { it.url }.toSet()
        val keywordAffinity = historyItems
            .asSequence()
            .map { it.title.lowercase() }
            .flatMap { title -> title.split(Regex("[^a-z0-9]+")) }
            .filter { token -> token.length >= 4 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(30)
            .map { it.key }
            .toSet()
        val themeTokens = HomeRecommendationThemeExtractor.extractThemeTokens(
            subscriptions = subscriptions,
            watchLater = watchLater,
        )
        val themeQueries = HomeRecommendationThemeExtractor.buildThemeQueries(themeTokens)
        return HomeRecommendationProfile(
            seenUrls = seenUrls,
            blockedVideos = blockedVideos,
            blockedChannels = blockedChannels,
            feedbackBlockedVideos = feedbackSignals.blockedVideos,
            feedbackBlockedChannels = feedbackSignals.blockedUploaders,
            subscriptionChannels = subscriptions.map { it.channelUrl }.toSet(),
            favoriteUrls = favoriteUrls,
            watchLaterUrls = watchLaterUrls,
            keywordAffinity = keywordAffinity,
            themeTokens = themeTokens,
            themeQueries = themeQueries,
        )
    }
}
