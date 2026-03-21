package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"

fun Route.streamRoutes(streamService: StreamService) {
    get("/streams") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        when (val result = streamService.getStreamInfo(url)) {
            is ExtractionResult.Success -> {
                call.response.headers.append(HttpHeaders.CacheControl, STREAMS_CACHE_CONTROL)
                call.respond(result.data)
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
