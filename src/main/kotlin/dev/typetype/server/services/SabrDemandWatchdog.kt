package dev.typetype.server.services

import kotlinx.coroutines.delay

internal class SabrDemandWatchdog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
) {
    suspend fun monitor(isAlive: () -> Boolean, holder: SabrSessionHolder): Boolean {
        while (isAlive()) {
            val state = holder.playbackState()
            if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return false
            val request = holder.nextSegmentDemand()
            if (request == null) {
                delay(intervalMs)
                continue
            }
            val futureLiveRequest = holder.isFutureLiveRequest(request)
            if (futureLiveRequest) {
                holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
            }
            val identity = holder.segmentDemandIdentity(request)
            val registeredAtMs = identity?.let { holder.segmentDemandRegisteredAtMs(request, it) }
            if (identity != null && registeredAtMs != null &&
                clock() - registeredAtMs >= SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS &&
                SabrDemandAttemptFinisher.expireStalledDemand(holder, request, identity, futureLiveRequest)
            ) {
                return true
            }
            delay(if (futureLiveRequest) maxOf(intervalMs, LIVE_EDGE_POLL_MS) else intervalMs)
        }
        return false
    }
}
