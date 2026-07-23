package dev.typetype.server.services

internal class SabrDemandDeadline(private val timeoutMs: Long) {
    private var identity: String? = null
    private var registeredAtMs = Long.MIN_VALUE
    private var expiresAtMs = Long.MIN_VALUE
    private var coveredBackoffUntilMs = Long.MIN_VALUE

    fun isExpired(
        identity: String,
        registeredAtMs: Long,
        nowMs: Long,
        backoffRemainingMs: Long,
    ): Boolean {
        if (this.identity != identity || this.registeredAtMs != registeredAtMs) {
            reset(identity, registeredAtMs)
        }
        extendForBackoff(nowMs, backoffRemainingMs)
        return nowMs >= expiresAtMs
    }

    private fun reset(identity: String, registeredAtMs: Long) {
        this.identity = identity
        this.registeredAtMs = registeredAtMs
        expiresAtMs = saturatedAdd(registeredAtMs, timeoutMs)
        coveredBackoffUntilMs = Long.MIN_VALUE
    }

    private fun extendForBackoff(nowMs: Long, remainingMs: Long) {
        if (remainingMs <= 0L) return
        val backoffUntilMs = saturatedAdd(nowMs, remainingMs)
        val uncoveredFromMs = maxOf(nowMs, coveredBackoffUntilMs)
        val uncoveredMs = (backoffUntilMs - uncoveredFromMs).coerceAtLeast(0L)
        expiresAtMs = saturatedAdd(expiresAtMs, uncoveredMs)
        coveredBackoffUntilMs = maxOf(coveredBackoffUntilMs, backoffUntilMs)
    }

    private fun saturatedAdd(value: Long, increment: Long): Long =
        if (value >= Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}
