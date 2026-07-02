package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.time.Instant

internal class SabrSessionPump {
    suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int) {
        val localization = Localization("en", "GB")
        var pumps = 0
        while (pumps < maxPumps && !bothFormatsKnown(holder) && !holder.session.isComplete) {
            holder.pumpMutex.withLock {
                runCatchingNonCancellation { holder.session.pumpOnce(localization) }
            }
            pumps++
        }
        holder.pumpMutex.withLock {
            SabrInitializationData.ingest(holder.audioFormat, holder)
            SabrInitializationData.ingest(holder.videoFormat, holder)
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
        val localization = Localization("en", "GB")
        if (request.isInitializationSegment) return fetchInitializationSegment(holder, request, localization)
        holder.markRequested(request)
        return fetchMediaSegment(holder, request, localization)
    }

    suspend fun pumpLoop(isAlive: () -> Boolean, holder: SabrSessionHolder, intervalMs: Long) {
        val localization = Localization("en", "GB")
        while (isAlive()) {
            try {
                val immediate = holder.pumpMutex.withLock { pumpRound(holder, localization) }
                delay(if (immediate) 0L else intervalMs)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                delay(ERROR_RETRY_MS)
            }
        }
    }

    private suspend fun fetchInitializationSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
    ): SabrMediaSegment? {
        return holder.pumpMutex.withLock {
            holder.session.getCachedSegment(request)?.let { segment ->
                holder.markServed(segment)
                return@withLock segment
            }
            if (holder.session.isBeyondEnd(request)) return@withLock null
            runCatchingNonCancellation { holder.session.fetchSegment(request, localization) }
                .getOrNull()
                ?.also { holder.markServed(it) }
        }
    }

    private suspend fun fetchMediaSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
    ): SabrMediaSegment? {
        if (shouldFillSequentially(holder, request)) {
            fetchSequentially(holder, request, localization)?.let { return it }
        }
        return fetchTargeted(holder, request, localization)
    }

    private suspend fun fetchSequentially(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
    ): SabrMediaSegment? {
        var pumps = 0
        while (pumps < SEQUENTIAL_PUMPS) {
            val segment = holder.pumpMutex.withLock {
                holder.session.getCachedSegment(request)?.let { cached ->
                    holder.markServed(cached)
                    return@withLock cached
                }
                if (holder.session.isBeyondEnd(request)) return@withLock null
                val edgeMs = holder.session.streamState.getMinBufferedEndMs()
                val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
                holder.session.configureTargetRequest(holder, request)
                if (startMs < edgeMs - REWIND_GAP_MS) {
                    holder.setReaderPosition(request.format, startMs.coerceAtLeast(0L))
                    holder.session.prepareForRewind(request)
                    runCatchingNonCancellation { holder.session.pumpOnce(localization) }
                    holder.session.clearTargetRequest(holder)
                    return@withLock holder.session.getCachedSegment(request)?.also { holder.markServed(it) }
                } else {
                    holder.session.streamState.setPlayerTimeMs(maxOf(edgeMs, startMs + PLAYER_TIME_OFFSET_MS))
                }
                val fetched = runCatchingNonCancellation { holder.session.fetchSegment(request, localization) }.getOrNull()
                holder.session.clearTargetRequest(holder)
                fetched?.also { holder.markServed(it) }
            }
            if (segment != null || holder.session.isBeyondEnd(request)) return segment
            pumps++
            delay(FETCH_RETRY_DELAY_MS)
        }
        return null
    }

    private suspend fun fetchTargeted(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
    ): SabrMediaSegment? {
        var attempt = 0
        while (attempt < FETCH_ATTEMPTS) {
            val segment = holder.pumpMutex.withLock {
                holder.session.getCachedSegment(request)?.let { cached ->
                    holder.markServed(cached)
                    return@withLock cached
                }
                if (holder.session.isBeyondEnd(request)) return@withLock null
                holder.session.configureTargetRequest(holder, request)
                val fetched = runCatchingNonCancellation { holder.session.fetchSegment(request, localization) }.getOrNull()
                holder.session.clearTargetRequest(holder)
                fetched?.also { holder.markServed(it) }
            }
            if (segment != null || holder.session.isBeyondEnd(request)) return segment
            attempt++
            delay(FETCH_RETRY_DELAY_MS)
        }
        return null
    }

    private fun shouldFillSequentially(holder: SabrSessionHolder, request: SabrSegmentRequest): Boolean {
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        return startMs <= INITIAL_SEQUENTIAL_LIMIT_MS || startMs <= edgeMs + SEQUENTIAL_FILL_AHEAD_MS
    }

    private fun pumpRound(holder: SabrSessionHolder, localization: Localization): Boolean {
        prepareEviction(holder)
        if (holder.session.requestNumber == 0) {
            holder.session.pumpOnce(localization)
            return true
        }
        holder.consumeRefetch()?.let { request ->
            holder.session.prepareForRewind(request)
            holder.session.pumpOnce(localization)
            return true
        }
        holder.consumeForwardSeek()?.let { request ->
            holder.session.prepareForForwardJump(request)
            holder.session.pumpOnce(localization)
            return true
        }
        if (holder.session.isComplete && !holder.hasPendingSeek()) return false
        if (isThrottled(holder)) return false
        holder.session.streamState.setPlayerTimeMs(holder.session.streamState.getMinBufferedEndMs())
        holder.session.pumpOnce(localization)
        return false
    }

    private fun prepareEviction(holder: SabrSessionHolder): Unit {
        holder.session.setPlayHeadMs((holder.readerTailMs() - BACK_BUFFER_MS).coerceAtLeast(0L))
        holder.session.evictPlayed()
    }

    private fun isThrottled(holder: SabrSessionHolder): Boolean {
        val edgeMs = holder.session.streamState.getMinBufferedEndMs()
        return edgeMs - holder.readerHeadMs() > READAHEAD_CUSHION_MS || holder.session.cachedBytes > MAX_AHEAD_BYTES
    }

    private companion object {
        const val ERROR_RETRY_MS = 1_000L
        const val FETCH_RETRY_DELAY_MS = 250L
        const val FETCH_ATTEMPTS = 3
        const val SEQUENTIAL_PUMPS = 24
        const val SEQUENTIAL_FILL_AHEAD_MS = 30_000L
        const val INITIAL_SEQUENTIAL_LIMIT_MS = 90_000L
        const val PLAYER_TIME_OFFSET_MS = 5L
        const val REWIND_GAP_MS = 2_000L
        const val READAHEAD_CUSHION_MS = 30_000L
        const val BACK_BUFFER_MS = 30_000L
        const val MAX_AHEAD_BYTES = 100L * 1024 * 1024
    }
}
