package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal object SabrDemandAttemptFinisher {
    fun expireStalledDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        recoverable: Boolean = false,
    ): Boolean = synchronized(holder) {
        val state = holder.playbackState()
        if (state == SabrPlaybackState.TERMINAL || state == SabrPlaybackState.NETWORK_FAILED) return@synchronized false
        val current = holder.nextSegmentDemand() ?: return@synchronized false
        if (!current.matches(request) || holder.segmentDemandIdentity(current) != identity) return@synchronized false
        fail(holder, request, identity, recoverable)
    }

    fun finish(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        result: YoutubeSabrSession.DemandResponseResult,
        runtime: SabrPumpRuntime,
        wasFutureLiveRequest: Boolean,
    ): Boolean = synchronized(holder) {
        if (!holder.isSegmentDemandActive(request, identity)) {
            runtime.finishDemand(identity)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return@synchronized true
        }
        val resolved = holder.resolveSegmentDemand(request, identity)
        SabrPumpLogger.finish(holder, "demand", request, result.segmentCount)
        if (!resolved && (wasFutureLiveRequest || holder.isFutureLiveRequest(request))) {
            runtime.finishDemand(identity)
            holder.setPlaybackState(SabrPlaybackState.WAITING_FOR_LIVE)
            return@synchronized false
        }
        val action = runtime.demandRecoveryAction(
            requestKey = identity,
            targetTrackSegmentCount = result.targetTrackSegmentCount,
            resolved = resolved,
        )
        val recovering = recover(holder, request, identity, action, runtime)
        if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
            holder.playbackState() != SabrPlaybackState.WAITING_FOR_LIVE
        ) {
            holder.setPlaybackState(SabrPlaybackState.IDLE)
        }
        resolved || recovering
    }

    private fun recover(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        action: SabrDemandRecoveryAction,
        runtime: SabrPumpRuntime,
    ): Boolean = when (action) {
        SabrDemandRecoveryAction.WAIT -> false
        SabrDemandRecoveryAction.READVERTISE_TRACK -> {
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForMissingSegment(request)
            SabrPumpLogger.recovery(holder, action, request)
            true
        }
        SabrDemandRecoveryAction.FAIL -> {
            runtime.finishDemand(identity)
            !fail(holder, request, identity)
        }
    }

    private fun fail(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        recoverable: Boolean = false,
    ): Boolean {
        val failed = holder.clearSegmentDemand(request, identity)
        if (failed) {
            holder.clearSegmentDemands()
            val message = "SABR demand stalled for ${request.summary()}"
            holder.failTerminal(if (recoverable) sabrRecoverableFailureMessage(message) else message)
        }
        return failed
    }

    private fun SabrSegmentRequest.matches(other: SabrSegmentRequest): Boolean =
        format.itag == other.format.itag && sequenceNumber == other.sequenceNumber && isInitializationSegment == other.isInitializationSegment

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
