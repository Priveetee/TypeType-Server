package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.mediaRequestsAt(playerTimeMs: Long): List<SabrSegmentRequest> = buildList {
    if (isVideoActive()) add(mediaRequestAt(videoFormat, playerTimeMs))
    if (isAudioActive()) add(mediaRequestAt(audioFormat, playerTimeMs))
}

private fun SabrSessionHolder.mediaRequestAt(
    format: YoutubeSabrFormat,
    playerTimeMs: Long,
): SabrSegmentRequest {
    val timeSequence = session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
    val sequence = lastServedSequence(format)?.let { last ->
        if (timeSequence <= last) last + 1 else timeSequence
    } ?: timeSequence
    return SabrSegmentRequest.media(format, sequence)
}
