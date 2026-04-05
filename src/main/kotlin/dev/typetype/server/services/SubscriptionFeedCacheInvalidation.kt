package dev.typetype.server.services

object SubscriptionFeedCacheInvalidation {
    @Volatile
    private var invalidator: SubscriptionFeedCacheInvalidator? = null

    fun configure(invalidator: SubscriptionFeedCacheInvalidator) {
        this.invalidator = invalidator
    }

    suspend fun invalidate(userId: String) {
        invalidator?.invalidate(userId)
    }
}
