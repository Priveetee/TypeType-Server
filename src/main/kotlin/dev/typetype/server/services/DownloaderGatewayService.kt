package dev.typetype.server.services

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class DownloaderGatewayResponse(
    val status: Int,
    val contentType: String?,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

class DownloaderGatewayService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun forward(method: String, path: String, query: String?, headers: Map<String, String>, body: ByteArray?): DownloaderGatewayResponse {
        val url = buildUrl(path, query)
        val requestBody = if (hasBody(method)) {
            val mediaType = headers["Content-Type"]?.toMediaTypeOrNull()
            (body ?: ByteArray(0)).toRequestBody(mediaType)
        } else {
            null
        }

        val requestBuilder = Request.Builder().url(url).method(method, requestBody)
        headers.forEach { (name, value) -> if (shouldForwardRequestHeader(name)) requestBuilder.addHeader(name, value) }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseHeaders = response.headers.names().flatMap { name -> response.headers(name).map { name to it } }
            return DownloaderGatewayResponse(
                status = response.code,
                contentType = response.header("Content-Type"),
                headers = responseHeaders,
                body = response.body?.bytes() ?: ByteArray(0),
            )
        }
    }

    private fun buildUrl(path: String, query: String?): String {
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        val withPath = "${baseUrl.trimEnd('/')}$cleanPath"
        return if (query.isNullOrBlank()) withPath else "$withPath?$query"
    }

    private fun hasBody(method: String): Boolean = method == "POST" || method == "PUT" || method == "PATCH"

    private fun shouldForwardRequestHeader(name: String): Boolean {
        val lower = name.lowercase()
        return lower != "host" && lower != "content-length" && lower != "connection"
    }
}
