package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.DownloaderGatewayService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.net.URI

fun Route.downloaderGatewayRoutes(gateway: DownloaderGatewayService) {
    route("/downloader") {
        head { forwardDownloaderRequest(call, gateway) }
        get { forwardDownloaderRequest(call, gateway) }
        post { forwardDownloaderRequest(call, gateway) }
        put { forwardDownloaderRequest(call, gateway) }
        patch { forwardDownloaderRequest(call, gateway) }
        delete { forwardDownloaderRequest(call, gateway) }
        options { forwardDownloaderRequest(call, gateway) }
    }
    route("/downloader/{...}") {
        head { forwardDownloaderRequest(call, gateway) }
        get { forwardDownloaderRequest(call, gateway) }
        post { forwardDownloaderRequest(call, gateway) }
        put { forwardDownloaderRequest(call, gateway) }
        patch { forwardDownloaderRequest(call, gateway) }
        delete { forwardDownloaderRequest(call, gateway) }
        options { forwardDownloaderRequest(call, gateway) }
    }
}

private suspend fun forwardDownloaderRequest(call: ApplicationCall, gateway: DownloaderGatewayService) {
    val requestMethod = call.request.httpMethod.value
    val method = if (requestMethod == "HEAD") "GET" else requestMethod
    val path = call.request.path().removePrefix("/downloader").ifBlank { "/" }
    val query = call.request.queryString().takeIf { it.isNotBlank() }
    val requestHeaders = call.request.headers.names().associateWith { call.request.headers[it].orEmpty() }
    val body = if (hasRequestBody(method)) call.receiveText().toByteArray() else null

    val response = runCatching { gateway.forward(method, path, query, requestHeaders, body) }
        .getOrElse {
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("downloader unavailable"))
            return
        }

    val effectiveResponse = if (shouldProxyArtifact(path, response)) {
        val location = headerValue(response, "Location")
        if (location == null) {
            response
        } else {
            runCatching { gateway.fetchAbsolute(requestMethod, location, requestHeaders) }
                .getOrElse {
                    call.respond(HttpStatusCode.BadGateway, ErrorResponse("artifact unavailable"))
                    return
                }
        }
    } else {
        response
    }

    effectiveResponse.headers.forEach { (name, value) ->
        if (shouldForwardResponseHeader(name)) call.response.headers.append(name, value, safeOnly = false)
    }

    val status = HttpStatusCode.fromValue(effectiveResponse.status)
    val contentType = effectiveResponse.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        ?: ContentType.Application.OctetStream
    call.respondBytes(effectiveResponse.body, contentType = contentType, status = status)
}

private fun hasRequestBody(method: String): Boolean = method == "POST" || method == "PUT" || method == "PATCH"

private fun shouldProxyArtifact(path: String, response: dev.typetype.server.services.DownloaderGatewayResponse): Boolean {
    if (!path.endsWith("/artifact")) return false
    if (response.status != 302 && response.status != 307) return false
    val location = headerValue(response, "Location") ?: return false
    return isInternalHost(location)
}

private fun headerValue(response: dev.typetype.server.services.DownloaderGatewayResponse, name: String): String? =
    response.headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

private fun isInternalHost(location: String): Boolean {
    val host = runCatching { URI(location).host }.getOrNull() ?: return false
    return host.equals("garage", ignoreCase = true)
}

private fun shouldForwardResponseHeader(name: String): Boolean {
    val lower = name.lowercase()
    return lower != "content-length" && lower != "transfer-encoding" && lower != "connection"
}
