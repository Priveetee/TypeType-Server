package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrRecoverableException
import java.io.IOException

internal class SabrSessionPumpLoop(private val segmentCache: SabrSegmentCache?) {
    suspend fun run(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "GB")
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
            } catch (_: Exception) {
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
        if (holder.session.requestNumber == 0) {
            pumpOnceAndCache(holder, localization)
            return true
        }
        holder.consumeRefetch()?.let { request ->
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForRewind(request)
            pumpOnceAndCache(holder, localization)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        holder.consumeForwardSeek()?.let { request ->
            holder.setPlaybackState(SabrPlaybackState.REPOSITIONING)
            holder.session.prepareForForwardJump(request)
            pumpOnceAndCache(holder, localization)
            holder.setPlaybackState(SabrPlaybackState.IDLE)
            return true
        }
        if (holder.session.isComplete && !holder.hasPendingSeek()) return false
        if (isThrottled(holder)) {
            holder.setPlaybackState(SabrPlaybackState.THROTTLED)
            return false
        }
        holder.setPlaybackState(SabrPlaybackState.REQUESTING)
        holder.session.streamState.setPlayerTimeMs(holder.session.streamState.getMinBufferedEndMs())
        pumpOnceAndCache(holder, localization)
        holder.setPlaybackState(SabrPlaybackState.IDLE)
        return false
    }

    private suspend fun pumpOnceAndCache(holder: SabrSessionHolder, localization: Localization): List<SabrMediaSegment> =
        holder.session.pumpOnce(localization).also { segmentCache?.putAll(holder, it) }

    private fun prepareEviction(holder: SabrSessionHolder): Unit {
        holder.session.setPlayHeadMs((holder.readerTailMs() - SabrPumpPolicy.backBufferMs(holder)).coerceAtLeast(0L))
        holder.session.evictPlayed()
    }

    private fun isThrottled(holder: SabrSessionHolder): Boolean {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        return edgeMs - holder.readerHeadMs() > SabrPumpPolicy.READAHEAD_CUSHION_MS ||
            holder.session.cachedBytes > SabrPumpPolicy.MAX_AHEAD_BYTES
    }
}
