package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal fun SabrSessionHolder.failLivePlaybackDiscontinuity(
    format: YoutubeSabrFormat,
    targetMs: Long,
    segment: CachedSabrSegment,
    hasBufferedMedia: Boolean,
): Boolean {
    if (!hasBufferedMedia || livePlaybackSnapshot()?.active != true) return false
    if (segment.startMs <= targetMs + MAX_CONTIGUOUS_LIVE_GAP_MS) return false
    failTerminal(sabrRecoverableFailureMessage("live ${format.itag} media discontinuity"))
    return true
}

private const val MAX_CONTIGUOUS_LIVE_GAP_MS = 500L
