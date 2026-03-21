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
            allowLive = true,
        )
        val subscriptionUrls = subscriptions.map { it.url }.toSet()
        val discovery = scoreAndFilter(
            candidates = discoveryCandidates,
            profile = profile,
            scorer = { video, p -> HomeRecommendationScoring.scoreDiscovery(video, p) },
            allowLive = false,
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
    ): List<VideoItem> {
        val byUrl = linkedMapOf<String, HomeRecommendationScoredVideo>()
        candidates.forEach { video ->
            if (video.url.isBlank()) return@forEach
            if (video.url in profile.seenUrls || video.url in profile.blockedVideos) return@forEach
            if (video.uploaderUrl.isNotBlank() && video.uploaderUrl in profile.blockedChannels) return@forEach
            if (!allowLive && HomeRecommendationLiveTitleDetector.isLiveLike(video.title)) return@forEach
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
