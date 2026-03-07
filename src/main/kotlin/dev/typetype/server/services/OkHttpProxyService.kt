package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

class OkHttpProxyService : ProxyService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun pipe(url: String, rangeHeader: String?): ExtractionResult<ProxyResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanUrl = url
                    .replace(Regex("[&?]cpn=[^&]*"), "")
                    .replace(Regex("[&?]pppid=[^&]*"), "")
                val builder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                if (rangeHeader != null) builder.header("Range", rangeHeader)
                client.newCall(builder.build()).execute()
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful && response.code != 206) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val stream: InputStream = body?.byteStream() ?: InputStream.nullInputStream()
                        ExtractionResult.Success(
                            ProxyResponse(
                                status = response.code,
                                contentType = response.header("Content-Type") ?: "application/octet-stream",
                                contentLength = response.header("Content-Length")?.toLongOrNull(),
                                contentRange = response.header("Content-Range"),
                                acceptRanges = response.header("Accept-Ranges"),
                                stream = stream,
                                close = response::close,
                            )
                        )
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
