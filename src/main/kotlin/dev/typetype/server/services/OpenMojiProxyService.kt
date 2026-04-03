package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class OpenMojiProxyService(private val cache: CacheService) {

    private val cooldownUntilEpochMs = AtomicLong(0L)
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getSvg(code: String): ByteArray? {
        val key = cacheKey(code)
        runCatching { cache.get(key) }.getOrNull()?.let { return it.toByteArray(Charsets.UTF_8) }
        val now = System.currentTimeMillis()
        if (now < cooldownUntilEpochMs.get()) return null
        return when (val fetched = fetch(code)) {
            is FetchResult.Success -> {
                cooldownUntilEpochMs.set(0L)
                runCatching { cache.set(key, fetched.bytes.toString(Charsets.UTF_8), SVG_CACHE_TTL_SECONDS) }
                fetched.bytes
            }
            FetchResult.NotFound -> null
            FetchResult.Failed -> {
                cooldownUntilEpochMs.set(now + FAILURE_COOLDOWN_MS)
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
    }

    private sealed interface FetchResult {
        data class Success(val bytes: ByteArray) : FetchResult
        data object NotFound : FetchResult
        data object Failed : FetchResult
    }
}
