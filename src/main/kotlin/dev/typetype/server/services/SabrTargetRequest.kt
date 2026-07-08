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
        prepareForTarget(request, targetPlayerTimeMs)
        pumpOnceStreamingUntilCached(localization, request)
        getCachedSegment(request)
    }
    return result.getOrNull()
        ?.takeIf { it.matches(request) }
}

private fun YoutubeSabrSession.prepareForTarget(request: SabrSegmentRequest, targetPlayerTimeMs: Long): Unit {
    if (request.isInitializationSegment) return
    val edgeMs = streamState.getMinBufferedEndMs()
    val startMs = streamState.getSegmentStartMs(request.format, request.sequenceNumber)
    when {
        startMs < edgeMs -> prepareForRewind(request)
        startMs > edgeMs + TARGET_SEGMENT_OFFSET_MS -> prepareForForwardJump(request)
        else -> prepareForMediaSegment(request)
    }
    streamState.setPlayerTimeMs(targetPlayerTimeMs.coerceAtLeast(0L))
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
