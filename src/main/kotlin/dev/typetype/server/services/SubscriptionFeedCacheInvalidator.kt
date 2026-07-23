package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

class SubscriptionFeedCacheInvalidator(
    private val cache: CacheService,
    private val feedService: SubscriptionFeedService,
) {
    suspend fun invalidate(userId: String) {
        feedService.invalidate(userId)
        runCatching { cache.delete(SubscriptionFeedCacheKeys.shorts(userId)) }
    }

    suspend fun awaitRefresh(userId: String) {
        feedService.awaitRefresh(userId)
    }
}
