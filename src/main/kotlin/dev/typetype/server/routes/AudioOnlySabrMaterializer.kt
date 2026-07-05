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
    val output = ByteArrayOutputStream()
    output.write(init)
    var sequence = 1
    while (sequence <= MAX_SABR_AUDIO_ONLY_SEGMENTS) {
        val endSegment = holder.session.streamState.getEndSegment(holder.audioFormat).toInt()
        if (endSegment > 0 && sequence > endSegment) return output.toByteArray()
        val request = SabrSegmentRequest.media(holder.audioFormat, sequence)
        val segment = sabrSessionStore.fetchSegment(holder, request)
            ?: return if (holder.session.isBeyondEnd(request) && sequence > 1) output.toByteArray() else null
        if (
            segment.header.isInitSegment ||
            segment.header.itag != holder.audioFormat.itag ||
            segment.header.sequenceNumber != sequence
        ) return null
        output.write(segment.data)
        sequence++
    }
    return null
}

private const val MAX_SABR_AUDIO_ONLY_SEGMENTS = 10_000
