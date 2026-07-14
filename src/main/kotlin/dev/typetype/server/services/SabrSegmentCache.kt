package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrSegmentCache {
    fun get(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? =
        holder.cachedSegment(key(holder, request))

    fun put(holder: SabrSessionHolder, segment: SabrMediaSegment): Unit {
        val format = holder.formatForItag(segment.header.itag) ?: return
        val cached = segment.toCachedSabrSegment(format.mimeType.orEmpty())
        val key = key(holder, format, cached.init, cached.sequence)
        holder.putCachedSegment(key, cached)
    }

    fun putAll(holder: SabrSessionHolder, segments: List<SabrMediaSegment>): Unit {
        segments.forEach { put(holder, it) }
    }

    private fun SabrSessionHolder.formatForItag(itag: Int): YoutubeSabrFormat? = when (itag) {
        audioFormat.itag -> audioFormat
        videoFormat.itag -> videoFormat
        else -> null
    }

    private fun key(holder: SabrSessionHolder, request: SabrSegmentRequest): String =
        key(holder, request.format, request.isInitializationSegment, request.sequenceNumber)

    private fun key(holder: SabrSessionHolder, format: YoutubeSabrFormat, init: Boolean, sequence: Int): String =
        listOf(
            "sabr-segment-v1",
            holder.key.videoId,
            holder.sessionToken,
            format.itag.toString(),
            format.lastModified.toString(),
            format.xtags.orEmpty(),
            if (init) "init" else sequence.toString(),
        ).joinToString(":")
}
