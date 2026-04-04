package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationShortSubscriptionSource {
    suspend fun fetch(
        userId: String,
        subscriptionShortsFeedService: SubscriptionShortsFeedService,
        subscriptionFeedService: SubscriptionFeedService,
    ): List<VideoItem> {
        val pureSubscriptions = subscriptionShortsFeedService.getFeed(userId = userId, page = 0, limit = 180).videos
        if (pureSubscriptions.isNotEmpty()) return pureSubscriptions
        return subscriptionFeedService.getCachedFeed(userId = userId, page = 0, limit = 160)
            ?.videos
            .orEmpty()
            .filter { it.isShortFormContent || it.duration in 1L..85L }
    }
}
