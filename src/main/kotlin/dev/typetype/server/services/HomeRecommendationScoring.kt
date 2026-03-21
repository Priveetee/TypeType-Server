package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationScoring {
    fun scoreSubscription(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val base = 1.25
        return base + commonSignals(video, profile) + 0.25
    }

    fun scoreDiscovery(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val base = 0.95
        return base + commonSignals(video, profile)
    }

    private fun commonSignals(video: VideoItem, profile: HomeRecommendationProfile): Double {
        val recency = recencyBoost(video.uploaded)
        val channelBoost = profile.channelAffinity[video.uploaderUrl] ?: 0.0
        val keywordBoost = keywordBoost(video, profile.keywordAffinity)
        val subscriptionBoost = if (video.uploaderUrl in profile.subscriptionChannels) 0.2 else 0.0
        val favoriteBoost = if (video.url in profile.favoriteUrls) 0.15 else 0.0
        val watchLaterBoost = if (video.url in profile.watchLaterUrls) 0.08 else 0.0
        return recency + channelBoost + keywordBoost + subscriptionBoost + favoriteBoost + watchLaterBoost
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
}
