package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrSegmentCache(private val cache: CacheService?) {
    suspend fun get(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? {
        val key = key(holder, request)
        holder.cachedSegment(key)?.let {
            logger.info("sabr_cache event=l1_hit videoId={} itag={} sequence={}", holder.key.videoId, request.format.itag, request.sequenceNumber)
            return it
        }
        if (cache == null) return null
        val value = cache.get(key)
        logger.info("sabr_cache event={} videoId={} itag={} sequence={}", if (value == null) "miss" else "hit", holder.key.videoId, request.format.itag, request.sequenceNumber)
        return value?.let { runCatching { CacheJson.decodeFromString<CachedSabrSegment>(it) }.getOrNull() }
            ?.also { holder.putCachedSegment(key, it) }
    }

    suspend fun put(holder: SabrSessionHolder, segment: SabrMediaSegment): Unit {
        val format = holder.formatForItag(segment.header.itag) ?: return
        val cached = segment.toCachedSabrSegment(format.mimeType.orEmpty())
        val key = key(holder, format, cached.init, cached.sequence)
        holder.putCachedSegment(key, cached)
        if (cache == null) return
        cache.set(key, CacheJson.encodeToString(cached), TTL_SECONDS)
        logger.info("sabr_cache event=store videoId={} itag={} sequence={} bytes={}", holder.key.videoId, cached.itag, cached.sequence, cached.length)
    }

    suspend fun putAll(holder: SabrSessionHolder, segments: List<SabrMediaSegment>): Unit {
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

    private companion object {
        val logger = LoggerFactory.getLogger(SabrSegmentCache::class.java)
        const val TTL_SECONDS = 180L
    }
}
