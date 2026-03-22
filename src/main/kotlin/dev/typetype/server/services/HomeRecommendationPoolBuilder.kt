package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

class HomeRecommendationPoolBuilder {
    fun build(
        profile: HomeRecommendationProfile,
        subscriptionCandidates: List<VideoItem>,
        discoveryCandidates: List<VideoItem>,
    ): HomeRecommendationPool {
        val subscriptions = scoreAndFilter(
            candidates = subscriptionCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreSubscription(video, p) },
            allowLive = false,
            minThemeScore = 0.0,
        )
        val subscriptionUrls = subscriptions.map { it.url }.toSet()
        val discoveryMinThemeScore = if (profile.themeTokens.size < 8) 0.24 else 0.34
        val discovery = scoreAndFilter(
            candidates = discoveryCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreDiscovery(video, p) },
            allowLive = false,
            minThemeScore = discoveryMinThemeScore,
        ).filterNot { video -> video.url in subscriptionUrls }
        return HomeRecommendationPool(
            subscriptions = subscriptions,
            discovery = discovery,
        )
    }

    private fun scoreAndFilter(
        candidates: List<VideoItem>,
        profile: HomeRecommendationProfile,
        scorer: (VideoItem, HomeRecommendationProfile) -> Double,
        allowLive: Boolean,
        minThemeScore: Double,
    ): List<VideoItem> {
        val byUrl = linkedMapOf<String, HomeRecommendationScoredVideo>()
        candidates.forEach { video ->
            if (video.url.isBlank()) return@forEach
            if (video.url in profile.seenUrls || video.url in profile.blockedVideos) return@forEach
            if (video.url in profile.feedbackBlockedVideos) return@forEach
            if (video.uploaderUrl.isNotBlank() && video.uploaderUrl in profile.blockedChannels) return@forEach
            if (video.uploaderUrl.isNotBlank() && video.uploaderUrl in profile.feedbackBlockedChannels) return@forEach
            if (!allowLive && HomeRecommendationLiveTitleDetector.isLiveLike(video.title)) return@forEach
            if (minThemeScore > 0.0 && profile.themeTokens.isNotEmpty()) {
                val themeScore = HomeRecommendationThemeExtractor.computeThemeScore(
                    videoTitle = video.title,
                    channelName = video.uploaderName,
                    themeTokens = profile.themeTokens,
                )
                if (themeScore < minThemeScore) return@forEach
            }
            val scored = HomeRecommendationScoredVideo(video = video, score = scorer(video, profile))
            val current = byUrl[video.url]
            if (current == null || scored.score > current.score) {
                byUrl[video.url] = scored
            }
        }
        return byUrl.values
            .sortedWith(
                compareByDescending<HomeRecommendationScoredVideo> { it.score }
                    .thenByDescending { it.video.uploaded }
                    .thenBy { it.video.url }
            )
            .map { it.video }
    }
}
