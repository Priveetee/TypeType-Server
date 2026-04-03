package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OpenMojiProxyService(private val cache: CacheService) {

    private val localCache = ConcurrentHashMap<String, LocalSvg>()
    private val failedUntilByCode = ConcurrentHashMap<String, Long>()
    private val notFoundUntilByCode = ConcurrentHashMap<String, Long>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getSvg(code: String): ByteArray? {
        val key = cacheKey(code)
        val now = System.currentTimeMillis()
        localCache[code]?.takeIf { it.expiresAtMs > now }?.let { return it.bytes }
        if (now < (notFoundUntilByCode[code] ?: 0L)) return null
        if (now < (failedUntilByCode[code] ?: 0L)) return null
        runCatching { cache.get(key) }.getOrNull()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            localCache[code] = LocalSvg(bytes = bytes, expiresAtMs = now + LOCAL_CACHE_TTL_MS)
            return bytes
        }
        return when (val fetched = fetch(code)) {
            is FetchResult.Success -> {
                failedUntilByCode.remove(code)
                notFoundUntilByCode.remove(code)
                localCache[code] = LocalSvg(bytes = fetched.bytes, expiresAtMs = now + LOCAL_CACHE_TTL_MS)
                runCatching { cache.set(key, fetched.bytes.toString(Charsets.UTF_8), SVG_CACHE_TTL_SECONDS) }
                fetched.bytes
            }
            FetchResult.NotFound -> {
                failedUntilByCode.remove(code)
                notFoundUntilByCode[code] = now + NOT_FOUND_CACHE_MS
                null
            }
            FetchResult.Failed -> {
                failedUntilByCode[code] = now + FAILURE_COOLDOWN_MS
                null
            }
        }
    }

    private suspend fun fetch(code: String): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(openMojiCdnUrl(code)).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use FetchResult.NotFound
                if (!response.isSuccessful) return@use FetchResult.Failed
                val bytes = response.body?.bytes() ?: return@use FetchResult.Failed
                FetchResult.Success(bytes)
            }
        }.getOrElse { FetchResult.Failed }
    }

    private fun cacheKey(code: String): String = "openmoji:$code"

    private fun openMojiCdnUrl(code: String): String = "$OPENMOJI_CDN_BASE/$code.svg"

    companion object {
        private const val OPENMOJI_CDN_BASE = "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg"
        private const val SVG_CACHE_TTL_SECONDS = 604800L
        private const val FAILURE_COOLDOWN_MS = 15000L
        private const val NOT_FOUND_CACHE_MS = 300000L
        private const val LOCAL_CACHE_TTL_MS = 600000L
    }

    private sealed interface FetchResult {
        data class Success(val bytes: ByteArray) : FetchResult
        data object NotFound : FetchResult
        data object Failed : FetchResult
    }

    private data class LocalSvg(
        val bytes: ByteArray,
        val expiresAtMs: Long,
    )
}
