package dev.typetype.server

import dev.typetype.server.services.SubscriptionFeedCacheKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SubscriptionFeedCacheKeysTest {
    @Test
    fun `feed and shorts keys are deterministic and scoped`() {
        val feedA = SubscriptionFeedCacheKeys.feed("user-a")
        val feedAAgain = SubscriptionFeedCacheKeys.feed("user-a")
        val feedB = SubscriptionFeedCacheKeys.feed("user-b")
        val shortsA = SubscriptionFeedCacheKeys.shorts("user-a")
        assertEquals(feedA, feedAAgain)
        assertNotEquals(feedA, feedB)
        assertNotEquals(feedA, shortsA)
    }
}
