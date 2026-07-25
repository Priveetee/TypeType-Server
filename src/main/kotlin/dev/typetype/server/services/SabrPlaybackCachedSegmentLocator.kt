package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal suspend fun SabrSessionStore.findCachedPlaybackMediaAt(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
    predictedSequence: Int,
    allowFollowing: Boolean = false,
): CachedSabrSegment? {
    for (distance in 0..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(holder, format, predictedSequence + distance)?.let {
            if (it.coversPlaybackTime(holder, format, targetMs)) return it
        }
        if (distance > 0) {
            cachedMedia(holder, format, predictedSequence - distance)?.let {
                if (it.coversPlaybackTime(holder, format, targetMs)) return it
            }
        }
    }
    if (!allowFollowing) return null
    for (distance in 0..MAX_SEQUENCE_DISTANCE) {
        cachedMedia(holder, format, predictedSequence + distance)?.let {
            if (it.startsWithinFollowingRange(holder, format, targetMs, distance)) return it
        }
    }
    return null
}

private suspend fun SabrSessionStore.cachedMedia(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    sequence: Int,
): CachedSabrSegment? {
    if (sequence < 1) return null
    return cachedSegment(holder, SabrSegmentRequest.media(format, sequence))
}

internal fun CachedSabrSegment.coversPlaybackTime(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
): Boolean {
    val effectiveStartMs = startMs.takeIf { it >= 0L } ?: holder.playbackSegmentStartMs(format, sequence)
    val effectiveDurationMs = durationMs.takeIf { it > 0L } ?: holder.playbackSegmentDurationMs(format, sequence)
    return targetMs >= effectiveStartMs - TIMING_TOLERANCE_MS && targetMs < effectiveStartMs + effectiveDurationMs
}

private fun CachedSabrSegment.startsWithinFollowingRange(
    holder: SabrSessionHolder,
    format: YoutubeSabrFormat,
    targetMs: Long,
    sequenceDistance: Int,
): Boolean {
    val effectiveStartMs = startMs.takeIf { it >= 0L } ?: holder.playbackSegmentStartMs(format, sequence)
    val effectiveDurationMs = durationMs.takeIf { it > 0L } ?: holder.playbackSegmentDurationMs(format, sequence)
    val leadMs = effectiveStartMs - targetMs
    return leadMs in -TIMING_TOLERANCE_MS..maximumFollowingLeadMs(effectiveDurationMs, sequenceDistance)
}

private const val MAX_SEQUENCE_DISTANCE = 24
