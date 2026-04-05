package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

class SubscriptionFeedCacheInvalidator(private val cache: CacheService) {
    suspend fun invalidate(userId: String) {
        runCatching { cache.delete(SubscriptionFeedCacheKeys.feed(userId)) }
        runCatching { cache.delete(SubscriptionFeedCacheKeys.shorts(userId)) }
    }
}
