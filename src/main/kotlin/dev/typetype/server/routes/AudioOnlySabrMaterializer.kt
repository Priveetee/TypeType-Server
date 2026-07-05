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
    val endSegment = holder.session.streamState.getEndSegment(holder.audioFormat).toInt()
    if (endSegment <= 0 || endSegment > MAX_SABR_AUDIO_ONLY_SEGMENTS) return null
    val output = ByteArrayOutputStream()
    output.write(init)
    for (sequence in 1..endSegment) {
        val segment = sabrSessionStore.fetchSegment(holder, SabrSegmentRequest.media(holder.audioFormat, sequence))
            ?: return null
        if (segment.header.isInitSegment || segment.header.itag != holder.audioFormat.itag) return null
        output.write(segment.data)
    }
    return output.toByteArray()
}

private const val MAX_SABR_AUDIO_ONLY_SEGMENTS = 10_000
