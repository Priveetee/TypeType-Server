package dev.typetype.server.services

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

internal object SubscriptionMutationLock {
    fun acquire(userId: String) {
        val userKey = userId.hashCode() and Int.MAX_VALUE
        TransactionManager.current().exec(
            "SELECT pg_advisory_xact_lock($LOCK_NAMESPACE, $userKey)",
        )
    }

    private const val LOCK_NAMESPACE = 1_414_814_032
}
