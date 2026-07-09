package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.playbackStartSequence(format: YoutubeSabrFormat, playerTimeMs: Long): Int {
    val sequence = session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
    return if (format.isVideo) (sequence - VIDEO_PREROLL_SEGMENTS).coerceAtLeast(1) else sequence
}

private const val VIDEO_PREROLL_SEGMENTS = 1
