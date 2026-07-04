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
        else -> streamState.getSegmentStartMs(request.format, request.sequenceNumber)
            .coerceAtLeast(0L)
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
    return result.getOrNull()?.also { holder.markServed(it) }
}
