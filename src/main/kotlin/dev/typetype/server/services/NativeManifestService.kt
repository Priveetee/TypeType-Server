package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.StreamInfo

class NativeManifestService(private val httpClient: OkHttpClient) {

    private val WEBM_ADAPTATION_SET = Regex(
        """\s*<AdaptationSet[^>]*mimeType="(?:video|audio)/webm"[^>]*>[\s\S]*?</AdaptationSet>"""
    )

    suspend fun nativeManifest(videoUrl: String): ExtractionResult<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) { StreamInfo.getInfo(videoUrl) }
            }.fold(
                onSuccess = { buildManifest(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Extraction failed") }
            )
        }

    private fun buildManifest(info: StreamInfo): ExtractionResult<String> {
        val dashMpdUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() }
            ?: return ExtractionResult.Failure("No DASH manifest URL available")
        return runCatching {
            val raw = fetchManifest(dashMpdUrl)
            val filtered = WEBM_ADAPTATION_SET.replace(raw, "")
            ExtractionResult.Success(rewriteManifestUrls(filtered))
        }.getOrElse { ExtractionResult.Failure(it.message ?: "Manifest processing failed") }
    }

    private fun fetchManifest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
            .build()
        return httpClient.newCall(request).execute().use { it.body?.string() ?: "" }
    }
}
