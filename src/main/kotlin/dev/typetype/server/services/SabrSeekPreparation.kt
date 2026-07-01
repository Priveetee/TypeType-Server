package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
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
    streamState.setBufferedRangesOverride(bufferedRanges(holder, request))
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

private fun YoutubeSabrSession.bufferedRanges(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
): List<SabrBufferedRange> {
    val target = request.format
    val companion = if (target.isAudio) holder.videoFormat else holder.audioFormat
    val previous = (request.sequenceNumber - 1).coerceAtLeast(1)
    val startMs = streamState.getSegmentStartMs(target, previous).coerceAtLeast(0L)
    val endMs = streamState.getSegmentEndMs(target, previous).coerceAtLeast(startMs + 1L)
    return listOf(
        SabrBufferedRange(target.itag, target.lastModified, target.xtags, startMs, endMs - startMs, previous, previous, 1000),
        SabrBufferedRange(companion.itag, companion.lastModified, companion.xtags, 0, MAX_RANGE_MS, MAX_RANGE, MAX_RANGE, 1000),
    )
}

private const val AUDIO_ONLY_TRACKS = 1
private const val VIDEO_ONLY_TRACKS = 2
private const val MAX_RANGE = Int.MAX_VALUE
private const val MAX_RANGE_MS = Int.MAX_VALUE.toLong()
