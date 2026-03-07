package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NativeManifestService(private val streamService: StreamService) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun nativeManifest(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val dashMpdUrl = result.data.dashMpdUrl
        if (dashMpdUrl.isBlank())
            return ExtractionResult.BadRequest("No native DASH manifest available")
        return withContext(Dispatchers.IO) {
            runCatching { fetchAndRewrite(dashMpdUrl) }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Failed to fetch native manifest") }
            )
        }
    }

    private fun fetchAndRewrite(manifestUrl: String): String {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("User-Agent", MANIFEST_USER_AGENT)
            .build()
        val manifest = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                error("Manifest fetch returned ${response.code}")
            response.body?.string() ?: error("Empty manifest body")
        }
        return rewriteUrls(manifest)
    }

    private fun rewriteUrls(manifest: String): String =
        manifest.replace(GOOGLEVIDEO_URL_PATTERN) { match ->
            "/proxy?url=" + URLEncoder.encode(match.value, StandardCharsets.UTF_8)
        }

    companion object {
        private val GOOGLEVIDEO_URL_PATTERN =
            Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/[^"<\s]+""")
        private const val MANIFEST_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    private fun <T> ExtractionResult<T>.recast(): ExtractionResult<String> = when (this) {
        is ExtractionResult.Success -> ExtractionResult.Success(data.toString())
        is ExtractionResult.BadRequest -> ExtractionResult.BadRequest(message)
        is ExtractionResult.Failure -> ExtractionResult.Failure(message)
    }
}
