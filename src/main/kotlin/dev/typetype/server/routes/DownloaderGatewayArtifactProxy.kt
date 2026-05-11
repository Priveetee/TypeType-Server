package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.DownloaderGatewayResponse
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import okhttp3.Response

suspend fun forwardDownloaderArtifactRequest(
    call: ApplicationCall,
    gateway: DownloaderGatewayService,
    response: DownloaderGatewayResponse,
    requestHeaders: Map<String, String>,
    forceDownload: Boolean,
) {
    val location = artifactHeader(response, HttpHeaders.Location)
    if (location == null) {
        call.respond(HttpStatusCode.BadGateway, ErrorResponse("artifact unavailable"))
        return
    }

    val upstream = runCatching { gateway.openFetchAbsolute(location, requestHeaders) }
        .getOrElse {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("artifact unavailable"))
            return
        }

    val artifact = upstream
    val headers = artifactHeaders(artifact)
    headers.forEach { (name, value) ->
        if (shouldForwardArtifactResponseHeader(name, forceDownload)) {
            call.response.headers.append(name, value, safeOnly = false)
        }
    }
    if (forceDownload) applyArtifactDownloadHeaders(call, artifactResponse(artifact, headers))

    val status = HttpStatusCode.fromValue(artifact.code)
    val contentType = artifactContentType(artifact, forceDownload)
    try {
        call.respondOutputStream(contentType = contentType, status = status) {
            artifact.use { response ->
                response.body.byteStream().use { input ->
                    input.copyTo(this, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    } catch (error: Throwable) {
        artifact.close()
        throw error
    }
}

private fun artifactHeader(response: DownloaderGatewayResponse, name: String): String? =
    response.headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

private fun artifactHeaders(response: Response): List<Pair<String, String>> =
    response.headers.names().flatMap { name -> response.headers(name).map { name to it } }

private fun artifactResponse(response: Response, headers: List<Pair<String, String>>): DownloaderGatewayResponse =
    DownloaderGatewayResponse(
        status = response.code,
        contentType = response.header(HttpHeaders.ContentType),
        headers = headers,
        body = ByteArray(0),
    )

private fun artifactContentType(response: Response, forceDownload: Boolean): ContentType {
    if (forceDownload) return ContentType.Application.OctetStream
    return response.header(HttpHeaders.ContentType)
        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        ?: ContentType.Application.OctetStream
}

private fun shouldForwardArtifactResponseHeader(name: String, forceDownload: Boolean): Boolean {
    val lower = name.lowercase()
    if (lower == "transfer-encoding" || lower == "connection") return false
    if (lower == "content-length") return true
    return shouldForwardGatewayResponseHeader(name, forceDownload)
}
