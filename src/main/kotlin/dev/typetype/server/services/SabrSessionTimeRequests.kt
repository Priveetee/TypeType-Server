package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.mediaRequestsAt(playerTimeMs: Long): List<SabrSegmentRequest> =
    mediaRequestsAt(playerTimeMs, activeGeneration())

internal fun SabrSessionHolder.mediaRequestsAt(playerTimeMs: Long, generation: Long): List<SabrSegmentRequest> = buildList {
    if (isVideoActive() && needsMediaAt(videoFormat, playerTimeMs, generation)) add(mediaRequestAt(videoFormat, playerTimeMs, generation))
    if (isAudioActive() && needsMediaAt(audioFormat, playerTimeMs, generation)) add(mediaRequestAt(audioFormat, playerTimeMs, generation))
}

private fun SabrSessionHolder.needsMediaAt(format: YoutubeSabrFormat, playerTimeMs: Long, generation: Long): Boolean =
    (readerPosition(format, generation) ?: return true) <= playerTimeMs.coerceAtLeast(0L)

private fun SabrSessionHolder.mediaRequestAt(
    format: YoutubeSabrFormat,
    playerTimeMs: Long,
    generation: Long,
): SabrSegmentRequest {
    val timeSequence = session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
    val sequence = lastServedSequence(format, generation)?.let { last ->
        if (timeSequence <= last) last + 1 else timeSequence
    } ?: timeSequence
    return SabrSegmentRequest.media(format, sequence)
}
