package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OkHttpProxyService : ProxyService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun fetch(url: String): ExtractionResult<ProxyResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                client.newCall(request).execute()
            }.fold(
                onSuccess = { response ->
                    if (!response.isSuccessful) {
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val body = response.body?.bytes() ?: ByteArray(0)
                        val contentType = response.header("Content-Type") ?: "application/octet-stream"
                        ExtractionResult.Success(ProxyResponse(contentType = contentType, body = body))
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "Proxy fetch failed") }
            )
        }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
