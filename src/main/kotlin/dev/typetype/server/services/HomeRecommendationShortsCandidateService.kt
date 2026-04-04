package dev.typetype.server.services

class HomeRecommendationShortsCandidateService {
    suspend fun fetch(
        userId: String,
        serviceId: Int,
        profile: HomeRecommendationProfile,
        candidateService: HomeRecommendationCandidateService,
    ): HomeRecommendationCandidatePool {
        val subscriptions = candidateService.fetchSubscriptionCandidates(userId, HomeRecommendationPoolMode.FAST)
            .asSequence()
            .filter { it.isShortFormContent || it.duration in 1L..85L }
            .map { it.copy(isShortFormContent = true) }
            .take(180)
            .map { HomeRecommendationTaggedVideo(it, HomeRecommendationSourceTag.SUBSCRIPTION) }
            .toList()
        val discoveryCandidates = candidateService.fetchTrendingCandidates(serviceId)
            .asSequence()
            .map { it.copy(video = it.video.copy(isShortFormContent = it.video.isShortFormContent || it.video.duration in 1L..85L)) }
            .filter { it.video.isShortFormContent }
            .toList()
        val exploration = candidateService.fetchSearchCandidates(
            serviceId = serviceId,
            queries = HomeRecommendationExplorationQueryProvider.shortQueries(),
            maxQueries = 6,
            perQueryLimit = 10,
            source = HomeRecommendationSourceTag.DISCOVERY_EXPLORATION,
        ).asSequence()
            .map { it.copy(video = it.video.copy(isShortFormContent = it.video.isShortFormContent || it.video.duration in 1L..85L)) }
            .filter { it.video.isShortFormContent }
            .toList()
        val discovery = HomeRecommendationDiscoveryAssembler().build(
            profile = profile,
            candidates = discoveryCandidates + exploration,
            explorationCap = 80,
        )
        return HomeRecommendationCandidatePool(subscriptions = subscriptions, discovery = discovery)
    }
}
