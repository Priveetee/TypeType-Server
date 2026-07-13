package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.io.IOException

internal class SabrSessionPumpLoop(
    private val unauthorizedRecovery: SabrUnauthorizedResponseRecovery = SabrUnauthorizedResponseRecovery { null },
) {
    suspend fun run(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "US")
        val runtime = SabrPumpRuntime()
        var consecutiveIoErrors = 0
        while (isAlive() && holder.playbackState() != SabrPlaybackState.TERMINAL) {
            try {
                val immediate = holder.pumpMutex.withLock { pumpRound(holder, localization, runtime) }
                consecutiveIoErrors = 0
                delay(if (immediate) 0L else demandDelayMs(holder, intervalMs))
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                consecutiveIoErrors++
                if (consecutiveIoErrors >= SabrPumpPolicy.MAX_CONSECUTIVE_IO_ERRORS) {
                    holder.recordNetworkFailure(error.message)
                    return
                }
                delay(SabrPumpPolicy.ERROR_RETRY_MS)
            } catch (error: SabrRecoverableException) {
                holder.failTerminal(error.message)
                return
            } catch (error: ExtractionException) {
                holder.failTerminal(error.message)
                return
            } catch (error: OutOfMemoryError) {
                holder.failTerminal(error.message)
                return
            } catch (error: Exception) {
                SabrPumpLogger.failure(holder, error)
                delay(SabrPumpPolicy.ERROR_RETRY_MS)
            }
        }
        if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
            holder.playbackState() != SabrPlaybackState.NETWORK_FAILED
        ) {
            holder.setPlaybackState(SabrPlaybackState.STOPPED)
        }
    }

    private suspend fun pumpRound(
        holder: SabrSessionHolder,
        localization: Localization,
        runtime: SabrPumpRuntime,
    ): Boolean {
        prepareEviction(holder)
        holder.consumeRefetch()?.let { request ->
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForRewind(request)
            SabrPumpLogger.start(holder, "refetch", request)
            val pumped = pumpOnce(holder, localization, runtime)
            SabrPumpLogger.finish(holder, "refetch", request, pumped)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.consumeForwardSeek()?.let { request ->
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForForwardJump(request)
            SabrPumpLogger.start(holder, "forward_seek", request)
            val pumped = pumpUntilCached(holder, localization, request, runtime)
            SabrPumpLogger.finish(holder, "forward_seek", request, pumped.segmentCount)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.nextSegmentDemand()?.let { request ->
            SabrPumpLogger.start(holder, "demand", request)
            val result = pumpDemand(holder, localization, request, runtime)
            val resolved = holder.resolveSegmentDemand(request)
            SabrPumpLogger.finish(holder, "demand", request, result.segmentCount)
            val action = runtime.demandRecoveryAction(
                requestKey = request.summary(),
                targetTrackSegmentCount = result.targetTrackSegmentCount,
                resolved = resolved,
            )
            val recovering = recoverDemand(holder, request, action, runtime)
            if (holder.playbackState() != SabrPlaybackState.TERMINAL) {
                holder.setPlaybackState(SabrPlaybackState.IDLE)
            }
            return resolved || recovering
        }
        if (holder.session.requestNumber == 0) return false
        if (holder.session.isComplete && !holder.hasPendingSeek()) return false
        if (runtime.isThrottled(holder)) {
            holder.setPlaybackState(SabrPlaybackState.THROTTLED)
            return false
        }
        holder.setPlaybackState(SabrPlaybackState.REQUESTING)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        holder.session.streamState.setPlayerTimeMs(runtime.requestPlayerTimeMs(holder, edgeMs))
        pumpOnce(holder, localization, runtime)
        holder.setPlaybackState(SabrPlaybackState.IDLE)
        return false
    }

    private fun pumpOnce(holder: SabrSessionHolder, localization: Localization, runtime: SabrPumpRuntime): Int {
        return try {
            holder.session.pumpOnceStreaming(localization).also { unauthorizedRecovery.verify(holder) }
        } finally {
            runtime.recordRequest()
        }
    }

    private fun pumpUntilCached(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
        runtime: SabrPumpRuntime,
    ): YoutubeSabrSession.DemandResponseResult {
        return try {
            holder.session.pumpOnceStreamingForDemand(localization, request)
                .also { unauthorizedRecovery.verify(holder) }
        } finally {
            runtime.recordRequest()
        }
    }

    private fun pumpDemand(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
        runtime: SabrPumpRuntime,
    ): YoutubeSabrSession.DemandResponseResult {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        holder.setReaderPosition(request.format, startMs)
        return when {
            startMs < edgeMs -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForRewind(request)
                pumpUntilCached(holder, localization, request, runtime)
            }
            startMs > edgeMs + DEMAND_FORWARD_JUMP_MS -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForForwardJump(request)
                pumpUntilCached(holder, localization, request, runtime)
            }
            else -> {
                holder.setPlaybackState(SabrPlaybackState.REQUESTING)
                holder.session.streamState.setPlayerTimeMs(runtime.demandPlayerTimeMs(holder, edgeMs))
                pumpUntilCached(holder, localization, request, runtime)
            }
        }
    }

    private fun prepareEviction(holder: SabrSessionHolder): Unit {
        holder.session.setPlayHeadMs((holder.readerTailMs() - SabrPumpPolicy.backBufferMs(holder)).coerceAtLeast(0L))
        holder.session.evictPlayed()
    }

    private fun recoverDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
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
        SabrDemandRecoveryAction.REFETCH -> {
            runtime.activateSeekMode()
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForRewind(request)
            SabrPumpLogger.recovery(holder, action, request)
            true
        }
        SabrDemandRecoveryAction.FAIL -> {
            holder.clearSegmentDemand(request)
            holder.failTerminal("SABR demand stalled for ${request.summary()}")
            false
        }
    }

    private fun demandDelayMs(holder: SabrSessionHolder, intervalMs: Long): Long =
        if (holder.pendingSegmentDemandSummary() == null) intervalMs
        else maxOf(intervalMs, holder.session.demandBackoffRemainingMs)

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"

}
