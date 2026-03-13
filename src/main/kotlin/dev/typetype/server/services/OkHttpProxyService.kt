package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal val GOOGLEVIDEO_URL_REGEX = Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/\S+""")

internal fun stripTrackingParams(url: String): String =
    url.replace(Regex("[&?]cpn=[^&]*"), "")
        .replace(Regex("[&?]pppid=[^&]*"), "")

internal fun rewriteHlsManifest(manifest: String): String =
    manifest.replace(GOOGLEVIDEO_URL_REGEX) { match ->
        "/proxy?url=" + URLEncoder.encode(match.value, StandardCharsets.UTF_8)
    }

class OkHttpProxyService(private val client: OkHttpClient) : ProxyService {

    override suspend fun pipe(url: String, rangeHeader: String?): ExtractionResult<ProxyResponse> =
        withContext(Dispatchers.IO) {
            validateProxyUrl(url)?.let { return@withContext ExtractionResult.BadRequest(it) }
            runCatching {
                val cleanUrl = stripTrackingParams(url)
                val builder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                if (isBilibili(cleanUrl)) builder.header("Referer", BILIBILI_REFERER)
                if (rangeHeader != null) builder.header("Range", rangeHeader)
                client.newCall(builder.build()).execute()
            }.fold(
                onSuccess = { response ->
                    val body = response.body
                    if (!response.isSuccessful && response.code != 206) {
                        response.close()
                        ExtractionResult.Failure("Upstream returned ${response.code}")
                    } else {
                        val statusCode = response.code
                        val contentType = response.header("Content-Type") ?: "application/octet-stream"
                        val contentRange = response.header("Content-Range")
                        val acceptRanges = response.header("Accept-Ranges")
                        val contentLength = response.header("Content-Length")?.toLongOrNull()
                        if (isHls(contentType)) {
                            val rewritten = rewriteHlsManifest(body?.string() ?: "")
                            response.close()
                            ExtractionResult.Success(ProxyResponse(
                                status = statusCode,
                                contentType = contentType,
                                contentLength = null,
                                contentRange = null,
                                acceptRanges = null,
                                stream = ByteArrayInputStream(rewritten.toByteArray(StandardCharsets.UTF_8)),
                                close = {},
                            ))
                        } else {
                            ExtractionResult.Success(ProxyResponse(
                                status = statusCode,
                                contentType = contentType,
                                contentLength = contentLength,
                                contentRange = contentRange,
                                acceptRanges = acceptRanges,
                                stream = body?.byteStream() ?: InputStream.nullInputStream(),
                                close = response::close,
                            ))
                        }
                    }
                },
                onFailure = { ExtractionResult.Failure(it.message ?: "Proxy fetch failed") }
            )
        }

    private fun isBilibili(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrElse { "" }
        return host.contains("bilibili") || host.contains("bilivideo") || host.endsWith("hdslb.com") || host.contains("akamaized")
    }

    private fun isHls(contentType: String): Boolean =
        contentType.contains("mpegurl", ignoreCase = true)

    companion object {
        private const val BILIBILI_REFERER = "https://www.bilibili.com"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
