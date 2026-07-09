package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.playbackStartSequence(format: YoutubeSabrFormat, playerTimeMs: Long): Int {
    val sequence = session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
    return (sequence - PLAYBACK_PREROLL_SEGMENTS).coerceAtLeast(1)
}

private const val PLAYBACK_PREROLL_SEGMENTS = 1
