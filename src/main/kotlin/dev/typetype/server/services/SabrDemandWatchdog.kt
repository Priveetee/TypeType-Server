package dev.typetype.server.services

import kotlinx.coroutines.delay

internal class SabrDemandWatchdog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
) {
    suspend fun monitor(isAlive: () -> Boolean, holder: SabrSessionHolder): Boolean {
        var trackedIdentity: String? = null
        var targetBufferedEndMs = Long.MIN_VALUE
        var stalledSinceMs = 0L
        while (isAlive()) {
            val state = holder.playbackState()
            if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return false
            val request = holder.nextSegmentDemand()
            if (request == null) {
                trackedIdentity = null
                delay(intervalMs)
                continue
            }
            val identity = holder.segmentDemandIdentity(request)
            val currentTargetBufferedEndMs = holder.session.streamState.getBufferedEndMs(request.format)
            val nowMs = clock()
            if (identity == null || identity != trackedIdentity || currentTargetBufferedEndMs != targetBufferedEndMs) {
                trackedIdentity = identity
                targetBufferedEndMs = currentTargetBufferedEndMs
                stalledSinceMs = nowMs
            } else if (nowMs - stalledSinceMs >= SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS &&
                SabrDemandAttemptFinisher.expireStalledDemand(holder, request, identity)
            ) {
                return true
            }
            delay(intervalMs)
        }
        return false
    }
}
