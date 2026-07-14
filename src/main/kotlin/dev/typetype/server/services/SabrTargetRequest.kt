package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.slf4j.LoggerFactory

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
    result.onFailure { error ->
        SabrPlaybackDiagnostics.record(holder, request, error.message)
        logger.warn(
            "sabr_target event=fetch_failed videoId={} itag={} seq={} init={} targetMs={} errorType={} error={}",
            holder.key.videoId,
            request.format.itag,
            request.sequenceNumber,
            request.isInitializationSegment,
            targetPlayerTimeMs,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }
    return result.getOrNull()
        ?.takeIf { it.matches(request) }
        ?.also { SabrPlaybackDiagnostics.clear(holder, it) }
}

private fun YoutubeSabrSession.prepareForTarget(request: SabrSegmentRequest, targetPlayerTimeMs: Long): Unit {
    if (request.isInitializationSegment) return
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

private val logger = LoggerFactory.getLogger("SabrTargetRequest")

private const val TARGET_SEGMENT_OFFSET_MS = 1_000L
