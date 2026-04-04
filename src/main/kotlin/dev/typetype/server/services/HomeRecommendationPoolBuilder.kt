package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

class HomeRecommendationPoolBuilder {
    fun build(
        profile: HomeRecommendationProfile,
        subscriptionCandidates: List<HomeRecommendationTaggedVideo>,
        discoveryCandidates: List<HomeRecommendationTaggedVideo>,
    ): HomeRecommendationPool {
        val subscriptionsScored = scoreAndFilter(
            candidates = subscriptionCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreSubscription(video, p) },
            allowLive = false,
            minThemeScore = 0.0,
        )
        val jitteredSubscriptions = if (profile.personalizationEnabled) {
            HomeRecommendationJitter.apply(subscriptionsScored, profile.feedHistory)
        } else {
            subscriptionsScored
        }
        val subscriptionUrls = subscriptionsScored.map { it.video.url }.toSet()
        val discoveryScored = scoreAndFilter(
            candidates = discoveryCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreDiscovery(video, p) },
            allowLive = false,
            minThemeScore = 0.0,
        ).filterNot { scored -> scored.video.url in subscriptionUrls }
        val jitteredDiscovery = if (profile.personalizationEnabled) {
            HomeRecommendationJitter.apply(discoveryScored, profile.feedHistory)
        } else {
            discoveryScored
        }
        return HomeRecommendationPool(
            subscriptions = jitteredSubscriptions.map { it.video },
            discovery = jitteredDiscovery.map { it.video },
            subscriptionChannels = profile.subscriptionChannels,
            sourceByUrl = (jitteredSubscriptions + jitteredDiscovery).associate { it.video.url to it.source },
            sourceWeights = adjustWeights(profile),
        )
    }

    private fun adjustWeights(profile: HomeRecommendationProfile): Map<HomeRecommendationSourceTag, Double> {
        val sub = profile.subscriptionEngagement
        val disc = profile.discoveryEngagement
        if (sub == 0.0 && disc == 0.0) return emptyMap()
        val gap = (sub - disc).coerceIn(-8.0, 8.0)
        val subWeight = (1.0 + gap * 0.04).coerceIn(0.65, 1.5)
        val discWeight = (1.0 - gap * 0.03).coerceIn(0.65, 1.5)
        return mapOf(
            HomeRecommendationSourceTag.SUBSCRIPTION to subWeight,
            HomeRecommendationSourceTag.DISCOVERY_TRENDING to discWeight,
            HomeRecommendationSourceTag.DISCOVERY_THEME to discWeight,
            HomeRecommendationSourceTag.DISCOVERY_EXPLORATION to discWeight,
        )
    }

    private fun scoreAndFilter(
        candidates: List<HomeRecommendationTaggedVideo>,
        profile: HomeRecommendationProfile,
        scorer: (VideoItem, HomeRecommendationProfile) -> Double,
        allowLive: Boolean,
        minThemeScore: Double,
    ): List<HomeRecommendationScoredVideo> {
        val byUrl = linkedMapOf<String, HomeRecommendationScoredVideo>()
        candidates.forEach { tagged ->
            val video = tagged.video
            if (video.url.isBlank()) return@forEach
            if (video.url in profile.seenUrls || video.url in profile.blockedVideos) return@forEach
            if (video.url in profile.feedbackBlockedVideos || video.url in profile.implicitBlockedVideos) return@forEach
            if (video.uploaderUrl.isNotBlank() && video.uploaderUrl in profile.blockedChannels) return@forEach
            if (video.uploaderUrl.isNotBlank() && video.uploaderUrl in profile.feedbackBlockedChannels) return@forEach
            if (!allowLive && HomeRecommendationLiveTitleDetector.isLiveLike(video.title)) return@forEach
            if (minThemeScore > 0.0 && profile.themeTokens.isNotEmpty()) {
                val themeScore = HomeRecommendationThemeExtractor.computeThemeScore(video.title, video.uploaderName, profile.themeTokens)
                if (themeScore < minThemeScore) return@forEach
            }
            val rawScore = scorer(video, profile) * (profile.eventPenaltyByVideo[video.url] ?: 1.0)
            val score = if (profile.personalizationEnabled) {
                HomeRecommendationScoring.applyPersonalizationPenalties(video, rawScore, profile)
            } else {
                rawScore
            }
            val scored = HomeRecommendationScoredVideo(video = video, score = score, source = tagged.source)
            val current = byUrl[video.url]
            if (current == null || scored.score > current.score) byUrl[video.url] = scored
        }
        return byUrl.values.sortedWith(
            compareByDescending<HomeRecommendationScoredVideo> { it.score }
                .thenByDescending { it.video.uploaded }
                .thenBy { it.video.url },
        )
    }
}
