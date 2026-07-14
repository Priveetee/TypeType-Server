package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class HlsManifestService(
    private val streamService: StreamService,
    private val httpClient: OkHttpClient,
    cache: CacheService? = null,
    private val signManifestUrl: ((String) -> String)? = null,
    private val attestedYoutubeHls: suspend (String) -> String? = { null },
) {
    private val manifestCache = cache?.let(::HlsManifestCache)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ExtractionResult<String>>>()

    suspend fun hlsManifest(url: String, signManifestLinks: Boolean = false): ExtractionResult<String> {
        val manifestUrl = if (isManifestUrl(url)) {
            url
        } else {
            when (val resolved = resolveHlsUrl(url)) {
                is ExtractionResult.Success -> resolved.data
                is ExtractionResult.BadRequest -> return resolved
                is ExtractionResult.Failure -> return resolved
            }
        }
        return cachedOrFetch(manifestUrl, signManifestLinks)
    }

    suspend fun hlsManifestFromStreamInfo(
        result: ExtractionResult<StreamResponse>,
        signManifestLinks: Boolean = false,
    ): ExtractionResult<String> {
        val manifestUrl = when (val resolved = resolveHlsUrl(result, allowAttestedYoutubeHls = true)) {
            is ExtractionResult.Success -> resolved.data
            is ExtractionResult.BadRequest -> return resolved
            is ExtractionResult.Failure -> return resolved
        }
        return cachedOrFetch(manifestUrl, signManifestLinks)
    }

    private suspend fun cachedOrFetch(manifestUrl: String, signManifestLinks: Boolean): ExtractionResult<String> {
        val cacheKey = if (signManifestLinks) "signed:$manifestUrl" else manifestUrl
        manifestCache?.get(cacheKey)?.let { return ExtractionResult.Success(it) }
        val pending = CompletableDeferred<ExtractionResult<String>>()
        val existing = inFlight.putIfAbsent(cacheKey, pending)
        if (existing != null) return existing.await()
        return try {
            manifestCache?.get(cacheKey)?.let {
                val result = ExtractionResult.Success(it)
                pending.complete(result)
                return result
            }
            val result = fetchAndRewrite(manifestUrl, signManifestLinks)
            if (result is ExtractionResult.Success) manifestCache?.set(cacheKey, result.data)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(cacheKey, pending)
        }
    }

    private suspend fun resolveHlsUrl(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        return resolveHlsUrl(result, allowAttestedYoutubeHls = isYoutubeUrl(videoUrl))
    }

    private suspend fun resolveHlsUrl(
        result: ExtractionResult<StreamResponse>,
        allowAttestedYoutubeHls: Boolean = false,
    ): ExtractionResult<String> {
        if (result is ExtractionResult.BadRequest) return result
        if (result !is ExtractionResult.Success) return ExtractionResult.Failure("No HLS stream available for this video")
        if (allowAttestedYoutubeHls && result.data.isLive) {
            attestedYoutubeHls(result.data.id)?.let { return ExtractionResult.Success(it) }
        }
        val hls = result.data.hlsUrl
        return if (hls.isNotBlank()) ExtractionResult.Success(hls) else ExtractionResult.Failure("No HLS stream available for this video")
    }

    private suspend fun fetchAndRewrite(manifestUrl: String, signManifestLinks: Boolean): ExtractionResult<String> =
        withContext(Dispatchers.IO) {
            val hashIndex = manifestUrl.indexOf('#')
            val fetchUrl = if (hashIndex >= 0) manifestUrl.substring(0, hashIndex) else manifestUrl
            val fragment = if (hashIndex >= 0) manifestUrl.substring(hashIndex + 1) else ""
            val domandBid = fragment.takeIf { it.isNotBlank() }?.let(::parseNicoCookie)
            validateProxyUrl(fetchUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val request = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
                    .apply { if (domandBid != null) header("Cookie", "domand_bid=$domandBid") }
                    .build()
                httpClient.newCall(request).execute()
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val text = body.string()
                        response.close()
                        val rewritten = if (isNicoNicoManifest(fetchUrl)) {
                            rewriteNicoManifest(text, fetchUrl, domandBid, "../proxy")
                        } else {
                            rewriteYouTubeHlsManifest(text) { target ->
                                if (signManifestLinks) signManifestUrl?.invoke(target) ?: toHlsProxyUrl(target)
                                else toHlsProxyUrl(target)
                            }
                        }
                        ExtractionResult.Success(rewritten)
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "HLS manifest fetch failed") }
            )
        }

    private fun isNicoNicoManifest(url: String): Boolean =
        runCatching { URI(url).host.orEmpty().endsWith("nicovideo.jp") }.getOrDefault(false)
}
