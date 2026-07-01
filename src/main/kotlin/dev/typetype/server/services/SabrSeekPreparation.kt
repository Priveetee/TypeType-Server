package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.prepareForRequestedSegment(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    localization: Localization,
) {
    if (request.isInitializationSegment) return
    if (request.sequenceNumber > 1) primeInitialMedia(holder, localization)
    prepareForRewind(request)
    prepareForForwardJump(request)
    streamState.setBufferedRangesOverride(emptyList())
    val startMs = streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
    streamState.setPlayerTimeMs(if (startMs == 0L) 0L else startMs + 1L)
    if (request.format.isAudio) {
        selectTargetFormat(holder.audioFormat, holder.videoFormat)
    } else {
        selectTargetFormat(holder.videoFormat, holder.audioFormat)
    }
}

private fun YoutubeSabrSession.primeInitialMedia(holder: SabrSessionHolder, localization: Localization) {
    val requests = listOf(
        SabrSegmentRequest.initialization(holder.videoFormat),
        SabrSegmentRequest.initialization(holder.audioFormat),
        SabrSegmentRequest.media(holder.videoFormat, 1),
        SabrSegmentRequest.media(holder.audioFormat, 1),
    )
    for (request in requests) {
        if (getCachedSegment(request) == null && !isBeyondEnd(request)) {
            runCatching { fetchSegment(request, localization) }
        }
    }
}

private fun YoutubeSabrSession.selectTargetFormat(target: YoutubeSabrFormat, companion: YoutubeSabrFormat) {
    if (target.isAudio) {
        streamState.setRequestTrackMode(AUDIO_ONLY_TRACKS, true, false)
    } else {
        streamState.setRequestTrackMode(VIDEO_ONLY_TRACKS, false, true)
    }
    streamState.setFullyBuffered(companion, true)
    streamState.setFullyBuffered(target, false)
}

private const val AUDIO_ONLY_TRACKS = 1
private const val VIDEO_ONLY_TRACKS = 2
