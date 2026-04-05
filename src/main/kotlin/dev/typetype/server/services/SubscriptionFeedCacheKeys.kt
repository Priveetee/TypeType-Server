package dev.typetype.server.services

import java.security.MessageDigest

object SubscriptionFeedCacheKeys {
    fun feed(userId: String): String = "feed:${hash(userId)}"

    fun shorts(userId: String): String = "feed:shorts:${hash(userId)}"

    private fun hash(userId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(userId.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
