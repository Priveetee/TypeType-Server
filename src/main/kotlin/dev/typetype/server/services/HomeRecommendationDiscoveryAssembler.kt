package dev.typetype.server.services

class HomeRecommendationDiscoveryAssembler {
    fun build(
        profile: HomeRecommendationProfile,
        candidates: List<HomeRecommendationTaggedVideo>,
        explorationCap: Int,
    ): List<HomeRecommendationTaggedVideo> {
        val minThemeScore = if (profile.themeTokens.size < 8) 0.24 else 0.34
        val rawDiscovery = candidates
            .asSequence()
            .filter { tagged -> tagged.video.uploaderUrl !in profile.subscriptionChannels }
            .filter { tagged -> tagged.video.url !in profile.feedbackBlockedVideos }
            .filter { tagged -> tagged.video.uploaderUrl !in profile.feedbackBlockedChannels }
            .filter { tagged -> HomeRecommendationLiveTitleDetector.isLiveLike(tagged.video.title).not() }
            .filter { tagged -> (profile.channelInterest[tagged.video.uploaderUrl] ?: 0.0) > -1.5 }
            .distinctBy { tagged -> tagged.video.url }
            .toList()
        val languagePreferred = rawDiscovery.filter { tagged ->
            HomeRecommendationLanguageGate.isLikelyPreferred(tagged.video, profile)
        }
        val thematic = languagePreferred.filter { tagged ->
            HomeRecommendationThemeExtractor.computeThemeScore(
                tagged.video.title,
                tagged.video.uploaderName,
                profile.themeTokens,
            ) >= minThemeScore
        }
        val source = if (languagePreferred.isEmpty()) rawDiscovery else languagePreferred
        val thematicUrls = thematic.map { it.video.url }.toSet()
        val exploration = source.asSequence()
            .filter { tagged -> tagged.video.url !in thematicUrls }
            .take(explorationCap)
            .toList()
        return (thematic + exploration).distinctBy { tagged -> tagged.video.url }
    }
}
