package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun isManifestUrl(url: String): Boolean {
    if (!url.startsWith("http")) return false
    if (url.contains("/file/seg.ts")) return false
    return url.contains("manifest.googlevideo.com") || url.endsWith(".m3u8")
}

internal fun rewriteYouTubeHlsManifest(manifest: String): String {
    val uriAttr = Regex("""URI="([^"]+)"""")
    return manifest.lines().joinToString("\n") { line ->
        val t = line.trim()
        when {
            t.isBlank() -> line
            t.startsWith("#") -> uriAttr.replace(t) { mr ->
                val target = mr.groupValues[1]
                """URI="${toHlsProxyUrl(target)}""""
            }
            else -> toHlsProxyUrl(t)
        }
    }
}

private fun toHlsProxyUrl(url: String): String {
    val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8)
    return if (isManifestUrl(url)) "hls-manifest?url=$encoded" else "../proxy?url=$encoded"
}

class HlsManifestService(
    private val streamService: StreamService,
    private val httpClient: OkHttpClient,
) {

    suspend fun hlsManifest(url: String): ExtractionResult<String> {
        val manifestUrl = if (isManifestUrl(url)) {
            url
        } else {
            when (val resolved = resolveHlsUrl(url)) {
                is ExtractionResult.Success -> resolved.data
                is ExtractionResult.BadRequest -> return resolved
                is ExtractionResult.Failure -> return resolved
            }
        }
        return fetchAndRewrite(manifestUrl)
    }

    private suspend fun resolveHlsUrl(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result is ExtractionResult.BadRequest) return result
        if (result !is ExtractionResult.Success) return ExtractionResult.Failure("No HLS stream available for this video")
        val hls = result.data.hlsUrl
        return if (hls.isNotBlank()) ExtractionResult.Success(hls) else ExtractionResult.Failure("No HLS stream available for this video")
    }

    private suspend fun fetchAndRewrite(manifestUrl: String): ExtractionResult<String> =
        withContext(Dispatchers.IO) {
            validateProxyUrl(manifestUrl)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val request = Request.Builder()
                    .url(manifestUrl)
                    .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
                    .build()
                httpClient.newCall(request).execute()
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val text = body?.string() ?: ""
                        response.close()
                        ExtractionResult.Success(rewriteYouTubeHlsManifest(text))
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "HLS manifest fetch failed") }
            )
        }
}
