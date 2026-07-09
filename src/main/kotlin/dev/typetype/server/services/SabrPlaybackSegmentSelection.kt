package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.playbackStartSequence(format: YoutubeSabrFormat, playerTimeMs: Long): Int {
    return session.streamState.getSegmentNumberAtOrAfterTimeMs(format, playerTimeMs.coerceAtLeast(0L))
        .coerceAtLeast(1)
}
