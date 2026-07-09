package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.playbackStartSequence(format: YoutubeSabrFormat, playerTimeMs: Long): Int {
    val sequence = session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
    if (format.isAudio || sequence <= VIDEO_PREROLL_MIN_SEQUENCE) return sequence
    return (sequence - VIDEO_PREROLL_SEGMENTS).coerceAtLeast(1)
}

private const val VIDEO_PREROLL_SEGMENTS = 2
private const val VIDEO_PREROLL_MIN_SEQUENCE = 3
