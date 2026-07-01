package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant

internal class SabrSessionPump {
    suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int) {
        val localization = Localization("en", "GB")
        var pumps = 0
        while (pumps < maxPumps && !bothFormatsKnown(holder) && !holder.session.isComplete) {
            holder.pumpMutex.withLock {
                runCatching { holder.session.pumpOnce(localization) }
            }
            pumps++
        }
    }

    suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? {
        holder.lastRequestAt = Instant.now()
        holder.session.getCachedSegment(request)?.let { segment ->
            holder.markServed(segment)
            return segment
        }
        if (holder.session.isBeyondEnd(request)) return null
        val waiter = holder.pendingAwaits.computeIfAbsent(request) { CompletableDeferred() }
        holder.pendingSignals.add(request)
        return try {
            waiter.await()
            holder.session.getCachedSegment(request)?.also { holder.markServed(it) }
        } finally {
            holder.pendingAwaits.remove(request, waiter)
        }
    }

    suspend fun pumpLoop(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "GB")
        while (isAlive()) {
            try {
                holder.pumpMutex.withLock {
                    val signal = holder.pendingSignals.firstOrNull()
                    if (signal == null && holder.pendingAwaits.isEmpty() && bothFormatsKnown(holder)) {
                        holder.session.evictPlayed()
                        return@withLock
                    }
                    signal?.let {
                        holder.session.prepareForRequestedSegment(signal)
                        holder.pendingSignals.remove(signal)
                    }
                    holder.session.evictPlayed()
                    holder.session.pumpOnce(localization)
                }
                holder.completeReadyWaiters()
                delay(intervalMs)
            } catch (_: Exception) {
                delay(2_000)
            }
        }
    }

    private fun SabrSessionHolder.markServed(segment: SabrMediaSegment) {
        if (!segment.header.isInitSegment) {
            session.setPlayHeadMs(segment.header.startMs + segment.header.durationMs)
        }
    }

    private fun bothFormatsKnown(holder: SabrSessionHolder): Boolean {
        val state = holder.session.streamState
        return state.getEndSegment(holder.audioFormat) > 0L &&
            state.getEndSegment(holder.videoFormat) > 0L &&
            state.getSegmentEndMs(holder.audioFormat, 1) > 0L &&
            state.getSegmentEndMs(holder.videoFormat, 1) > 0L
    }

    private fun SabrSessionHolder.completeReadyWaiters() {
        for ((request, waiter) in pendingAwaits) {
            if (session.getCachedSegment(request) != null || session.isBeyondEnd(request)) {
                waiter.complete(Unit)
            }
        }
    }

    private fun YoutubeSabrSession.prepareForRequestedSegment(request: SabrSegmentRequest) {
        if (request.isInitializationSegment) return
        val max = streamState.getMaxSegment(request.format)
        when {
            request.sequenceNumber <= max -> prepareForRewind(request)
            request.sequenceNumber > max + 1 -> prepareForForwardJump(request)
        }
    }
}
