package dev.typetype.server.services

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class DownloaderGatewayResponse(
    val status: Int,
    val contentType: String?,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
)

class DownloaderGatewayService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build(),
) {
    fun forward(method: String, path: String, query: String?, headers: Map<String, String>, body: ByteArray?): DownloaderGatewayResponse {
        openForward(method, path, query, headers, body).use { response ->
            val responseHeaders = response.headers.names().flatMap { name -> response.headers(name).map { name to it } }
            return DownloaderGatewayResponse(
                status = response.code,
                contentType = response.header("Content-Type"),
                headers = responseHeaders,
                body = response.body.bytes(),
            )
        }
    }

    fun openForward(method: String, path: String, query: String?, headers: Map<String, String>, body: ByteArray?): Response {
        val url = buildUrl(path, query)
        val requestBody = if (hasBody(method)) {
            val mediaType = headers["Content-Type"]?.toMediaTypeOrNull()
            (body ?: ByteArray(0)).toRequestBody(mediaType)
        } else {
            null
        }

        val requestBuilder = Request.Builder().url(url).method(method, requestBody)
        headers.forEach { (name, value) -> if (shouldForwardRequestHeader(name)) requestBuilder.addHeader(name, value) }

        return client.newCall(requestBuilder.build()).execute()
    }

    fun fetchAbsolute(url: String, headers: Map<String, String>): DownloaderGatewayResponse {
        openFetchAbsolute(url, headers).use { response ->
            val responseHeaders = response.headers.names().flatMap { name -> response.headers(name).map { name to it } }
            return DownloaderGatewayResponse(
                status = response.code,
                contentType = response.header("Content-Type"),
                headers = responseHeaders,
                body = response.body.bytes(),
            )
        }
    }

    fun openFetchAbsolute(url: String, headers: Map<String, String>): Response {
        val requestBuilder = Request.Builder().url(url).method("GET", null)
        headers["Range"]?.takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Range", it) }
        return client.newCall(requestBuilder.build()).execute()
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
