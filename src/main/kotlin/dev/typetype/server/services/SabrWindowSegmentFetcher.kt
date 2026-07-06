package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal object SabrWindowSegmentFetcher {
    suspend fun fetch(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        localization: Localization,
        segmentCache: SabrSegmentCache?,
    ): SabrMediaSegment? {
        val segments = runCatchingNonCancellation {
            holder.session.fetchMediaAt(request.targetTimeMs(holder), true, true, localization)
        }.getOrNull().orEmpty()
        segments.forEach { segmentCache?.put(holder, it) }
        return segments.firstOrNull { it.matches(request) }
            ?: holder.session.getCachedSegment(request)
    }

    private fun SabrSegmentRequest.targetTimeMs(holder: SabrSessionHolder): Long {
        val start = holder.session.streamState.getSegmentStartMs(format, sequenceNumber).coerceAtLeast(0L)
        val end = holder.session.streamState.getSegmentEndMs(format, sequenceNumber)
        return if (end > start + 1L) minOf(start + TARGET_SEGMENT_OFFSET_MS, end - 1L) else start
    }

    private fun SabrMediaSegment.matches(request: SabrSegmentRequest): Boolean {
        if (header.isInitSegment || request.isInitializationSegment) return false
        return header.itag == request.format.itag && header.sequenceNumber == request.sequenceNumber
    }

    private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
}
