package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal object SabrDemandAttemptFinisher {
    fun finish(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        identity: String,
        result: YoutubeSabrSession.DemandResponseResult,
        runtime: SabrPumpRuntime,
    ): Boolean = synchronized(holder) {
        if (!holder.isSegmentDemandActive(request, identity)) {
            runtime.finishDemand(identity)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return@synchronized true
        }
        val resolved = holder.resolveSegmentDemand(request, identity)
        SabrPumpLogger.finish(holder, "demand", request, result.segmentCount)
        val action = runtime.demandRecoveryAction(
            requestKey = identity,
            targetTrackSegmentCount = result.targetTrackSegmentCount,
            resolved = resolved,
        )
        val recovering = recover(holder, request, identity, action, runtime)
        if (holder.playbackState() != SabrPlaybackState.TERMINAL) {
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
            val failed = holder.clearSegmentDemand(request, identity)
            if (failed) holder.failTerminal("SABR demand stalled for ${request.summary()}")
            !failed
        }
    }

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
