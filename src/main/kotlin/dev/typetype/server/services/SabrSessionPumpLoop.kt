package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory
import java.io.IOException

internal class SabrSessionPumpLoop {
    suspend fun run(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "US")
        var consecutiveIoErrors = 0
        while (isAlive()) {
            try {
                val immediate = holder.pumpMutex.withLock { pumpRound(holder, localization) }
                consecutiveIoErrors = 0
                delay(if (immediate) 0L else intervalMs)
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
                logger.warn(
                    "sabr_pump event=round_failed videoId={} state={} requestNumber={} edgeMs={} cachedBytes={} errorType={} error={}",
                    holder.key.videoId,
                    holder.playbackState(),
                    holder.session.requestNumber,
                    holder.session.streamState.getMinBufferedEndMs(),
                    holder.session.cachedBytes,
                    error.javaClass.simpleName,
                    error.message,
                    error,
                )
                delay(SabrPumpPolicy.ERROR_RETRY_MS)
            }
        }
        if (holder.playbackState() != SabrPlaybackState.TERMINAL &&
            holder.playbackState() != SabrPlaybackState.NETWORK_FAILED
        ) {
            holder.setPlaybackState(SabrPlaybackState.STOPPED)
        }
    }

    private suspend fun pumpRound(holder: SabrSessionHolder, localization: Localization): Boolean {
        prepareEviction(holder)
        holder.consumeRefetch()?.let { request ->
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForRewind(request)
            logPumpStart(holder, "refetch", request)
            val pumped = pumpOnce(holder, localization)
            logPumpFinish(holder, "refetch", request, pumped)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.consumeForwardSeek()?.let { request ->
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForForwardJump(request)
            logPumpStart(holder, "forward_seek", request)
            val pumped = pumpOnceTargeted(holder, localization, request)
            logPumpFinish(holder, "forward_seek", request, pumped)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.nextSegmentDemand()?.let { request ->
            logPumpStart(holder, "demand", request)
            val pumped = pumpDemand(holder, localization, request)
            holder.session.getCachedSegment(request)?.let { holder.clearSegmentDemand(request) }
            logPumpFinish(holder, "demand", request, pumped)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return pumped > 0
        }
        if (holder.session.requestNumber == 0) {
            logPumpStart(holder, "initial", null)
            val pumped = pumpOnce(holder, localization)
            logPumpFinish(holder, "initial", null, pumped)
            return true
        }
        if (holder.session.isComplete && !holder.hasPendingSeek()) return false
        if (isThrottled(holder)) {
            holder.setPlaybackState(SabrPlaybackState.THROTTLED)
            return false
        }
        holder.setPlaybackState(SabrPlaybackState.REQUESTING)
        holder.session.streamState.setPlayerTimeMs(holder.session.streamState.getMinBufferedEndMs())
        pumpOnce(holder, localization)
        holder.setPlaybackState(SabrPlaybackState.IDLE)
        return false
    }

    private fun logPumpStart(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?): Unit {
        logger.info(
            "sabr_pump event={}_start videoId={} request={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    private fun logPumpFinish(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?, pumped: Int): Unit {
        logger.info(
            "sabr_pump event={}_finish videoId={} request={} pumped={} cached={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            pumped,
            request?.let { holder.session.getCachedSegment(it) != null },
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    private fun pumpOnce(holder: SabrSessionHolder, localization: Localization): Int =
        holder.session.pumpOnceStreaming(localization)

    private fun pumpUntilCached(holder: SabrSessionHolder, localization: Localization, request: SabrSegmentRequest): Int =
        holder.session.pumpOnceStreamingUntilCached(localization, request)

    private fun pumpDemand(holder: SabrSessionHolder, localization: Localization, request: SabrSegmentRequest): Int {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        holder.setReaderPosition(request.format, startMs)
        return when {
            startMs < edgeMs -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForRewind(request)
                pumpUntilCachedTargeted(holder, localization, request)
            }
            startMs > edgeMs + DEMAND_FORWARD_JUMP_MS -> {
                holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
                holder.session.prepareForForwardJump(request)
                pumpUntilCachedTargeted(holder, localization, request)
            }
            else -> {
                holder.setPlaybackState(SabrPlaybackState.REQUESTING)
                holder.session.streamState.setPlayerTimeMs(maxOf(holder.playerTimeMs(), edgeMs))
                pumpUntilCachedTargeted(holder, localization, request)
            }
        }
    }

    private fun pumpOnceTargeted(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
    ): Int = withTargetedRequestShape(holder, request) { pumpOnce(holder, localization) }

    private fun pumpUntilCachedTargeted(
        holder: SabrSessionHolder,
        localization: Localization,
        request: SabrSegmentRequest,
    ): Int = withTargetedRequestShape(holder, request) { pumpUntilCached(holder, localization, request) }

    private fun prepareEviction(holder: SabrSessionHolder): Unit {
        holder.session.setPlayHeadMs((holder.readerTailMs() - SabrPumpPolicy.backBufferMs(holder)).coerceAtLeast(0L))
        holder.session.evictPlayed()
    }

    private fun isThrottled(holder: SabrSessionHolder): Boolean {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        return edgeMs - holder.readerHeadMs() > SabrPumpPolicy.READAHEAD_CUSHION_MS ||
            holder.session.cachedBytes > SabrPumpPolicy.MAX_AHEAD_BYTES
    }

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"

    private companion object {
        val logger = LoggerFactory.getLogger(SabrSessionPumpLoop::class.java)
    }
}
