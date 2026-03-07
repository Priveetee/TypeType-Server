package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ProxyService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.proxyRoutes(proxyService: ProxyService) {
    get("/proxy") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val rangeHeader = call.request.headers["Range"]

        when (val result = proxyService.pipe(url, rangeHeader)) {
            is ExtractionResult.Success -> {
                val proxy = result.data
                try {
                    val status = HttpStatusCode.fromValue(proxy.status)
                    val contentType = ContentType.parse(proxy.contentType)
                    proxy.contentRange?.let { call.response.headers.append("Content-Range", it) }
                    proxy.acceptRanges?.let { call.response.headers.append("Accept-Ranges", it) }
                    call.respondOutputStream(contentType, status, proxy.contentLength) {
                        withContext(Dispatchers.IO) { proxy.stream.copyTo(this@respondOutputStream) }
                    }
                } finally {
                    proxy.close()
                }
            }
            is ExtractionResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
