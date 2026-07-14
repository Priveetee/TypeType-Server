package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.durationMs(): Long {
    val audioEndMs = indexedEndMs(audioFormat)
    val videoEndMs = indexedEndMs(videoFormat)
    if (audioEndMs > 0L && videoEndMs > 0L) return maxOf(audioEndMs, videoEndMs)
    return maxOf(audioFormat.approxDurationMs, videoFormat.approxDurationMs, 0L)
}

internal fun SabrSessionHolder.indexedEndMs(format: YoutubeSabrFormat): Long {
    val endSequence = session.streamState.getEndSegment(format).toInt()
    return if (endSequence > 0) session.streamState.getSegmentEndMs(format, endSequence) else 0L
}

internal fun SabrSessionHolder.readyEndMs(format: YoutubeSabrFormat, requestedEndMs: Long): Long {
    val durationMs = indexedEndMs(format).takeIf { it > 0L } ?: format.approxDurationMs.coerceAtLeast(0L)
    return minOf(durationMs, requestedEndMs)
}

internal fun YoutubeSabrFormat.trackName(): String = if (isAudio) "audio" else "video"

internal fun SabrPlaybackWindowRequest.bufferedEndFor(
    format: YoutubeSabrFormat,
    requestedStartMs: Long,
): Long {
    val startMs = requestedStartMs.coerceAtLeast(0L)
    return bufferedRanges.asSequence()
        .filter { it.itag == format.itag && it.startMs <= startMs && it.endMs > startMs }
        .maxOfOrNull { it.endMs }
        ?.coerceAtLeast(startMs)
        ?: startMs
}
