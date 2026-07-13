package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchSabrPump(
    pump: SabrSessionPump,
    registry: SabrSessionRegistry,
    holder: SabrSessionHolder,
    intervalMs: Long,
): Unit {
    if (holder.playbackState() == SabrPlaybackState.TERMINAL || !holder.markPumpStarted()) return
    if (holder.playbackState() == SabrPlaybackState.NETWORK_FAILED) {
        holder.setPlaybackState(SabrPlaybackState.IDLE)
    }
    launch {
        try {
            pump.pumpLoop({ registry.contains(holder.key) }, holder, intervalMs)
        } finally {
            holder.markPumpStopped()
        }
    }
}
