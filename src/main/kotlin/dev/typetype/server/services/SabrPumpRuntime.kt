package dev.typetype.server.services

internal class SabrPumpRuntime(private val clock: () -> Long = System::currentTimeMillis) {
    private val startedAtMs = clock()
    private var lastRequestMs = 0L
    private var seekModeUntilMs = 0L
    private var demandKey: String? = null
    private var demandSinceMs = 0L
    private var demandTrackReadvertised = false
    private var demandResponsesWithoutTarget = 0
    private var lastDemandRefetchMs = -1L

    fun activateSeekMode(): Unit {
        seekModeUntilMs = clock() + SabrPumpPolicy.SEEK_MODE_MS
    }

    fun recordRequest(): Unit {
        lastRequestMs = clock()
    }

    fun requestPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        if (isStartupBurst()) cappedServerAheadPlayerTimeMs(holder, edgeMs) else holder.playerTimeMs()

    fun demandPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        cappedServerAheadPlayerTimeMs(holder, edgeMs)

    fun demandRecoveryAction(
        requestKey: String,
        targetTrackSegmentCount: Int,
        resolved: Boolean,
    ): SabrDemandRecoveryAction {
        if (resolved) {
            resetDemandRecovery()
            return SabrDemandRecoveryAction.WAIT
        }
        val now = clock()
        if (demandKey != requestKey) {
            demandKey = requestKey
            demandSinceMs = now
            demandTrackReadvertised = false
            demandResponsesWithoutTarget = 0
            lastDemandRefetchMs = -1L
        }
        if (targetTrackSegmentCount > 0) {
            demandResponsesWithoutTarget++
            if (demandResponsesWithoutTarget >= SabrPumpPolicy.MAX_DEMAND_RESPONSES_WITHOUT_TARGET) {
                return SabrDemandRecoveryAction.FAIL
            }
            if (!demandTrackReadvertised) {
                demandTrackReadvertised = true
                return SabrDemandRecoveryAction.READVERTISE_TRACK
            }
        }
        val stalledMs = now - demandSinceMs
        if (stalledMs >= SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS) {
            return SabrDemandRecoveryAction.FAIL
        }
        if (stalledMs >= SabrPumpPolicy.DEMAND_RECOVERY_AFTER_NO_PROGRESS_MS &&
            (lastDemandRefetchMs < 0L || now - lastDemandRefetchMs >= SabrPumpPolicy.DEMAND_RECOVERY_RETRY_MS)
        ) {
            lastDemandRefetchMs = now
            return SabrDemandRecoveryAction.REFETCH
        }
        return SabrDemandRecoveryAction.WAIT
    }

    fun isThrottled(holder: SabrSessionHolder): Boolean {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val aheadMs = (edgeMs - holder.playerTimeMs()).coerceAtLeast(0L)
        return aheadMs >= targetReadaheadCushionMs(holder) && !isHeartbeatDue(holder) ||
            holder.session.cachedBytes > SabrPumpPolicy.MAX_AHEAD_BYTES
    }

    internal fun targetReadaheadCushionMs(holder: SabrSessionHolder): Long {
        if (isSeekMode()) return SabrPumpPolicy.SEEK_READAHEAD_CUSHION_MS
        if (isStartupBurst()) return SabrPumpPolicy.STARTUP_BURST_READAHEAD_CUSHION_MS
        if (holder.playerTimeMs() == 0L && holder.readerTailMs() == 0L) {
            return SabrPumpPolicy.STARTUP_READAHEAD_CUSHION_MS
        }
        val policy = holder.session.streamState.nextRequestPolicy ?: return SabrPumpPolicy.READAHEAD_CUSHION_MS
        val serverTargetMs = maxOf(policy.targetAudioReadaheadMs, policy.targetVideoReadaheadMs)
        if (serverTargetMs <= 0) return SabrPumpPolicy.READAHEAD_CUSHION_MS
        return serverTargetMs.toLong().coerceIn(
            SabrPumpPolicy.MIN_SERVER_READAHEAD_CUSHION_MS,
            SabrPumpPolicy.READAHEAD_CUSHION_MS,
        )
    }

    private fun cappedServerAheadPlayerTimeMs(holder: SabrSessionHolder, edgeMs: Long): Long =
        maxOf(holder.playerTimeMs(), edgeMs - SabrPumpPolicy.SERVER_AHEAD_MARGIN_MS)

    private fun isHeartbeatDue(holder: SabrSessionHolder): Boolean {
        val maximumMs = holder.session.streamState.nextRequestPolicy?.maxTimeSinceLastRequestMs ?: -1
        return maximumMs > 0 && lastRequestMs > 0L && clock() - lastRequestMs >= maximumMs
    }

    private fun isStartupBurst(): Boolean = clock() - startedAtMs < SabrPumpPolicy.STARTUP_BURST_MS

    private fun isSeekMode(): Boolean = clock() < seekModeUntilMs

    private fun resetDemandRecovery(): Unit {
        demandKey = null
        demandSinceMs = 0L
        demandTrackReadvertised = false
        demandResponsesWithoutTarget = 0
        lastDemandRefetchMs = -1L
    }
}
