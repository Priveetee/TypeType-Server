package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession

internal fun YoutubeSabrSession.configureTargetRequest(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
): Unit {
    val target = request.format
    val companion = if (target.isAudio) holder.videoFormat else holder.audioFormat
    streamState.setBufferedRangesOverride(listOf(bufferedRange(holder, target), fullRange(companion)))
    val startMs = streamState.getSegmentStartMs(target, request.sequenceNumber).coerceAtLeast(0L)
    streamState.setPlayerTimeMs(startMs + PLAYER_TIME_OFFSET_MS)
    if (target.isAudio) {
        streamState.setSelectVideoFormatBeforeAudio(false)
        streamState.setAudioOnlyRequestMode()
    } else {
        streamState.setSelectVideoFormatBeforeAudio(true)
        streamState.setVideoOnlyRequestMode()
    }
    streamState.setFullyBuffered(companion, true)
    streamState.setFullyBuffered(target, false)
}

internal fun YoutubeSabrSession.clearTargetRequest(holder: SabrSessionHolder): Unit {
    streamState.setBufferedRangesOverride(null)
    streamState.clearPlayerTimeMsOverride()
    streamState.setFullyBuffered(holder.audioFormat, false)
    streamState.setFullyBuffered(holder.videoFormat, false)
    streamState.setSelectVideoFormatBeforeAudio(false)
    streamState.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
}

private fun YoutubeSabrSession.bufferedRange(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
): SabrBufferedRange {
    val sequence = holder.lastServedSequence(format) ?: streamState.getMaxSegment(format).coerceAtLeast(1)
    val startMs = streamState.getSegmentStartMs(format, sequence).coerceAtLeast(0L)
    val endMs = streamState.getSegmentEndMs(format, sequence).coerceAtLeast(startMs + 1L)
    return SabrBufferedRange(format.itag, format.lastModified, format.xtags, 0L, endMs - startMs, sequence, sequence, 1000)
}

private fun fullRange(format: YoutubeSabrFormat): SabrBufferedRange =
    SabrBufferedRange(format.itag, format.lastModified, format.xtags, 0L, MAX_RANGE_MS, MAX_RANGE, MAX_RANGE, 1000)

private const val MAX_RANGE = Int.MAX_VALUE
private const val MAX_RANGE_MS = Int.MAX_VALUE.toLong()
private const val PLAYER_TIME_OFFSET_MS = 5L
