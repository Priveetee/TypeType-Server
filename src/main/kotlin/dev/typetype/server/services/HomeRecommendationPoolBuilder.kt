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
        val subscriptionUrls = subscriptionsScored.map { it.video.url }.toSet()
        val discoveryScored = scoreAndFilter(
            candidates = discoveryCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreDiscovery(video, p) },
            allowLive = false,
            minThemeScore = 0.0,
        ).filterNot { scored -> scored.video.url in subscriptionUrls }
        return HomeRecommendationPool(
            subscriptions = subscriptionsScored.map { it.video },
            discovery = discoveryScored.map { it.video },
            subscriptionChannels = profile.subscriptionChannels,
            sourceByUrl = (subscriptionsScored + discoveryScored).associate { it.video.url to it.source },
            sourceWeights = emptyMap(),
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
            val score = scorer(video, profile) * (profile.eventPenaltyByVideo[video.url] ?: 1.0)
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
