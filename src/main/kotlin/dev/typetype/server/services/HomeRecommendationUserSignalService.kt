package dev.typetype.server.services

class HomeRecommendationUserSignalService(
    private val subscriptionsService: SubscriptionsService,
    private val historyService: HistoryService,
    private val favoritesService: FavoritesService,
    private val watchLaterService: WatchLaterService,
    private val blockedService: BlockedService,
    private val recommendationEventService: RecommendationEventService,
    private val feedHistoryService: RecommendationFeedHistoryService,
    private val feedbackSignalService: RecommendationFeedbackSignalService,
    private val interestProfileService: RecommendationInterestProfileService,
) {
    suspend fun loadProfile(userId: String, personalizationEnabled: Boolean): HomeRecommendationProfile {
        return loadProfile(
            userId = userId,
            personalizationEnabled = personalizationEnabled,
            sessionContext = HomeRecommendationSessionContext(
                intent = HomeRecommendationSessionIntent.AUTO,
                deviceClass = HomeRecommendationDeviceClass.UNKNOWN,
            ),
        )
    }

    suspend fun loadProfile(
        userId: String,
        personalizationEnabled: Boolean,
        sessionContext: HomeRecommendationSessionContext,
    ): HomeRecommendationProfile {
        val subscriptions = subscriptionsService.getAll(userId)
        val favorites = if (personalizationEnabled) favoritesService.getAll(userId) else emptyList()
        val watchLater = if (personalizationEnabled) watchLaterService.getAll(userId) else emptyList()
        val historyItems = if (personalizationEnabled) {
            historyService.search(userId = userId, q = null, from = null, to = null, limit = 240, offset = 0).first
        } else {
            emptyList()
        }
        val blockedVideos = blockedService.getVideos(userId).map { it.url }.toSet()
        val blockedChannels = blockedService.getChannels(userId).map { it.url }.toSet()
        val feedbackSignals = feedbackSignalService.load(userId)
        val events = if (personalizationEnabled) recommendationEventService.getAll(userId) else emptyList()
        val eventSignals = HomeRecommendationEventAnalyzer.buildSignals(events)
        val interestProfile = if (personalizationEnabled) interestProfileService.load(userId) else RecommendationInterestProfile(emptyMap(), emptyMap())
        val feedHistory = if (personalizationEnabled) feedHistoryService.load(userId) else emptyMap()
        val seenUrls = if (personalizationEnabled) historyItems.map { it.url }.toSet() else emptySet()
        val favoriteUrls = favorites.map { it.videoUrl }.toSet()
        val watchLaterUrls = watchLater.map { it.url }.toSet()
        val keywordAffinity = if (personalizationEnabled) historyItems
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
            .toSet() else emptySet()
        val themeTokens = if (personalizationEnabled) {
            HomeRecommendationThemeExtractor.extractThemeTokens(subscriptions = subscriptions, watchLater = watchLater)
        } else {
            emptySet()
        }
        val subscriptionChannels = subscriptions.map { it.channelUrl }.toSet()
        val engagementSplit = if (personalizationEnabled) {
            HomeRecommendationEngagementSplitCalculator.compute(events, subscriptionChannels)
        } else {
            HomeRecommendationEngagementSplit(0.0, 0.0)
        }
        val themeQueries = if (personalizationEnabled) HomeRecommendationThemeExtractor.buildThemeQueries(themeTokens) else emptyList()
        return HomeRecommendationProfile(
            seenUrls = seenUrls,
            blockedVideos = blockedVideos,
            blockedChannels = blockedChannels,
            feedbackBlockedVideos = feedbackSignals.blockedVideos,
            feedbackBlockedChannels = feedbackSignals.blockedUploaders,
            subscriptionChannels = subscriptionChannels,
            favoriteUrls = favoriteUrls,
            watchLaterUrls = watchLaterUrls,
            keywordAffinity = keywordAffinity,
            themeTokens = themeTokens,
            themeQueries = themeQueries,
            channelInterest = interestProfile.channelScores,
            topicInterest = interestProfile.topicScores,
            eventPenaltyByVideo = eventSignals.videoPenalty,
            implicitBlockedVideos = eventSignals.implicitBlockedVideos,
            subscriptionEngagement = engagementSplit.subscriptionEngagement,
            discoveryEngagement = engagementSplit.discoveryEngagement,
            feedHistory = feedHistory,
            rejectionTopicPenalty = eventSignals.rejectionTopicPenalty,
            rejectionChannelPenalty = eventSignals.rejectionChannelPenalty,
            channelTopicProfile = HomeRecommendationSignalProfileBuilder.buildChannelTopicProfile(historyItems),
            shortsTopicInterest = HomeRecommendationSignalProfileBuilder.buildShortsTopicInterest(events),
            rejectionTopicPairPenalty = HomeRecommendationSignalProfileBuilder.buildTopicPairPenalty(events),
            creatorMomentum = HomeRecommendationSignalProfileBuilder.buildCreatorMomentum(events),
            personalizationEnabled = personalizationEnabled,
        )
    }
}
