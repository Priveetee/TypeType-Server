package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.fetchTargetedSegment(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
    playerTimeMs: Long? = null,
): SabrMediaSegment? {
    val targetPlayerTimeMs = when {
        playerTimeMs != null -> playerTimeMs
        request.isInitializationSegment -> 0L
        else -> targetTimeInsideSegment(request)
    }
    val result = runCatchingNonCancellation {
        fetchMediaSegmentAt(
            request,
            targetPlayerTimeMs,
            holder.isVideoActive(),
            holder.isAudioActive(),
            localization,
        )
    }
    return result.getOrNull()
        ?.takeIf { it.matches(request) }
}

private fun SabrMediaSegment.matches(request: SabrSegmentRequest): Boolean {
    if (header.itag != request.format.itag) return false
    return if (request.isInitializationSegment) {
        header.isInitSegment
    } else {
        !header.isInitSegment && header.sequenceNumber == request.sequenceNumber
    }
}

private fun YoutubeSabrSession.targetTimeInsideSegment(request: SabrSegmentRequest): Long {
    val startMs = streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
    val nextStartMs = streamState.getSegmentStartMs(request.format, request.sequenceNumber + 1).coerceAtLeast(0L)
    if (nextStartMs <= startMs + 1L) return startMs
    return minOf(startMs + TARGET_SEGMENT_OFFSET_MS, nextStartMs - 1L)
}

private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
