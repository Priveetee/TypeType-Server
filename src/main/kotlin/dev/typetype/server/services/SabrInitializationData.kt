package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

internal object SabrInitializationData {
    private const val CACHE_TTL_SECONDS = 21_600L
    private val memoryCache = ConcurrentHashMap<String, ByteArray>()

    suspend fun ingest(
        format: YoutubeSabrFormat,
        holder: SabrSessionHolder,
        cache: CacheService? = null,
    ): Boolean {
        val data = fetch(format, cache) ?: return false
        return holder.session.streamState.ingestInitializationData(format, data)
    }

    suspend fun fetch(format: YoutubeSabrFormat, cache: CacheService? = null): ByteArray? {
        val key = cacheKey(format) ?: return null
        memoryCache[key]?.let { return it }
        cache?.getBytes(key)?.let { bytes ->
            memoryCache[key] = bytes
            return bytes
        }
        val bytes = fetchRemote(format) ?: return null
        memoryCache[key] = bytes
        cache?.setBytes(key, bytes, CACHE_TTL_SECONDS)
        return bytes
    }

    private fun fetchRemote(format: YoutubeSabrFormat): ByteArray? {
        val url = format.initializationUrl?.takeUnless { it.isBlank() } ?: return null
        val start = format.initRangeStart
        val end = format.initRangeEnd
        if (start < 0L || end < start) return null
        val headers = mapOf("Range" to listOf("bytes=$start-$end"))
        return runCatching { NewPipe.getDownloader().get(url, headers) }
            .getOrNull()
            ?.takeIf { it.responseCode() == 200 || it.responseCode() == 206 }
            ?.rawResponseBody()
            ?.takeIf { it.isNotEmpty() }
    }

    private suspend fun CacheService.getBytes(key: String): ByteArray? = runCatching {
        get(key)?.let { Base64.getDecoder().decode(it) }
    }.getOrNull()

    private suspend fun CacheService.setBytes(key: String, bytes: ByteArray, ttlSeconds: Long): Unit {
        runCatching { set(key, Base64.getEncoder().encodeToString(bytes), ttlSeconds) }
    }

    private fun cacheKey(format: YoutubeSabrFormat): String? {
        val url = format.initializationUrl?.takeUnless { it.isBlank() } ?: return null
        val start = format.initRangeStart
        val end = format.initRangeEnd
        if (start < 0L || end < start) return null
        val raw = listOf(format.itag, format.lastModified, format.xtags.orEmpty(), start, end, url)
            .joinToString("|")
        return "sabr:init:v1:${sha256(raw)}"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
