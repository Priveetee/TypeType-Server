package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationScoring {
    fun scoreSubscription(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val base = 1.25
        return base + commonSignals(video, profile) + 0.25
    }

    fun scoreDiscovery(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val base = 0.95
        val themeScore = HomeRecommendationThemeExtractor.computeThemeScore(
            videoTitle = video.title,
            channelName = video.uploaderName,
            themeTokens = profile.themeTokens,
        )
        val themeBoost = themeScore * 0.8
        val channelBoost = (profile.channelInterest[video.uploaderUrl] ?: 0.0).coerceIn(-5.0, 8.0) * 0.08
        val topicBoost = topicBoost(video, profile.topicInterest)
        return base + commonSignals(video, profile) + themeBoost + channelBoost + topicBoost
    }

    private fun commonSignals(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val recency = recencyBoost(video.uploaded)
        val keywordBoost = keywordBoost(video, profile.keywordAffinity)
        val temporalBoost = HomeRecommendationTemporalBoost.boost(video.title)
        val viralBoost = HomeRecommendationPersonalization.viralVelocityBoost(video)
        val curiosityBoost = HomeRecommendationPersonalization.curiosityBoost(video, profile)
        val serendipityBoost = HomeRecommendationPersonalization.serendipityBoost(video, profile)
        val channelProfileBoost = HomeRecommendationPersonalization.channelProfileBoost(video, profile)
        val subscriptionBoost = if (video.uploaderUrl in profile.subscriptionChannels) 0.2 else 0.0
        val favoriteBoost = if (video.url in profile.favoriteUrls) 0.15 else 0.0
        val watchLaterBoost = if (video.url in profile.watchLaterUrls) 0.08 else 0.0
        val livePenalty = if (HomeRecommendationLiveTitleDetector.isLiveLike(video.title)) -0.5 else 0.0
        return recency + keywordBoost + temporalBoost + viralBoost + curiosityBoost + serendipityBoost + channelProfileBoost + subscriptionBoost + favoriteBoost + watchLaterBoost + livePenalty
    }

    private fun keywordBoost(video: VideoItem, keywords: Set<String>): Double {
        if (keywords.isEmpty()) return 0.0
        val titleTokens = video.title.lowercase().split(Regex("[^a-z0-9]+"))
        val hits = titleTokens.count { token -> token in keywords }
        return (hits.coerceAtMost(4)) * 0.06
    }

    private fun recencyBoost(uploaded: Long): Double {
        if (uploaded <= 0) return 0.0
        val ageHours = (System.currentTimeMillis() - uploaded).coerceAtLeast(0L) / 3_600_000.0
        return 1.0 / (1.0 + ageHours / 60.0)
    }

    private fun topicBoost(video: VideoItem, topicInterest: Map<String, Double>): Double {
        if (topicInterest.isEmpty()) return 0.0
        val tokens = RecommendationTopicTokenizer.tokenize("${video.title} ${video.uploaderName}")
        if (tokens.isEmpty()) return 0.0
        val raw = tokens.sumOf { token -> topicInterest[token] ?: 0.0 }
        return raw.coerceIn(-4.0, 10.0) * 0.06
    }

    fun applyPersonalizationPenalties(video: VideoItem, score: Double, profile: HomeRecommendationProfile): Double {
        return HomeRecommendationPersonalization.applyPenalties(video, score, profile)
    }

}
