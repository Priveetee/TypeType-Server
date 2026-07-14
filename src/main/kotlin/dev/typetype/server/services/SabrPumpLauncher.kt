package dev.typetype.server.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchSabrPump(
    pump: SabrSessionPump,
    registry: SabrSessionRegistry,
    holder: SabrSessionHolder,
    intervalMs: Long,
): Unit {
    val state = holder.playbackState()
    if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED || !holder.markPumpStarted()) return
    launch {
        try {
            pump.pumpLoop({ registry.contains(holder.key) }, holder, intervalMs)
        } finally {
            holder.markPumpStopped()
        }
    }
}
