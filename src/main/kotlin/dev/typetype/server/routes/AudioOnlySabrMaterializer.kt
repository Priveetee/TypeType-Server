package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.io.ByteArrayOutputStream

internal suspend fun materializeSabrAudioOnlyBody(
    sabrSessionStore: SabrSessionStore,
    holder: SabrSessionHolder,
    init: ByteArray,
): ByteArray? {
    val videoWasActive = holder.isVideoActive()
    val audioWasActive = holder.isAudioActive()
    holder.setActiveTracks(videoActive = false, audioActive = true)
    return try {
        materializeSabrAudioOnlyBodyActive(sabrSessionStore, holder, init)
    } finally {
        holder.setActiveTracks(videoActive = videoWasActive, audioActive = audioWasActive)
    }
}

private suspend fun materializeSabrAudioOnlyBodyActive(
    sabrSessionStore: SabrSessionStore,
    holder: SabrSessionHolder,
    init: ByteArray,
): ByteArray? {
    val output = ByteArrayOutputStream()
    output.write(init)
    var sequence = 1
    while (sequence <= MAX_SABR_AUDIO_ONLY_SEGMENTS) {
        val endSegment = holder.session.streamState.getEndSegment(holder.audioFormat).toInt()
        if (endSegment > 0 && sequence > endSegment) return output.toByteArray()
        val request = SabrSegmentRequest.media(holder.audioFormat, sequence)
        val bytes = sabrSessionStore.fetchAudioBytes(holder, request, sequence)
            ?: return if (holder.session.isBeyondEnd(request) && sequence > 1) output.toByteArray() else null
        output.write(bytes)
        sequence++
    }
    return null
}

private suspend fun SabrSessionStore.fetchAudioBytes(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    sequence: Int,
): ByteArray? {
    cachedSegment(holder, request)?.let { cached ->
        if (!cached.init && cached.itag == holder.audioFormat.itag && cached.sequence == sequence) return cached.bytes
    }
    val startMs = holder.audioTargetTimeMs(sequence)
    fetchMediaAt(holder, startMs)?.firstOrNull { segment ->
        !segment.header.isInitSegment &&
            segment.header.itag == holder.audioFormat.itag &&
            segment.header.sequenceNumber == sequence
    }?.let { return it.data }
    return fetchSegment(holder, request)?.takeIf { segment ->
        !segment.header.isInitSegment &&
            segment.header.itag == holder.audioFormat.itag &&
            segment.header.sequenceNumber == sequence
    }?.data
}

private fun SabrSessionHolder.audioTargetTimeMs(sequence: Int): Long {
    val startMs = session.streamState.getSegmentStartMs(audioFormat, sequence).coerceAtLeast(0L)
    val nextStartMs = session.streamState.getSegmentStartMs(audioFormat, sequence + 1).coerceAtLeast(0L)
    if (nextStartMs <= startMs + 1L) return startMs
    return minOf(startMs + SABR_AUDIO_ONLY_TARGET_OFFSET_MS, nextStartMs - 1L)
}

private const val MAX_SABR_AUDIO_ONLY_SEGMENTS = 10_000
private const val SABR_AUDIO_ONLY_TARGET_OFFSET_MS = 1_000L
