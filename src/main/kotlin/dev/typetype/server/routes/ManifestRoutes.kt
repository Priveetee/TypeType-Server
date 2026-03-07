package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ManifestService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.manifestRoutes(manifestService: ManifestService) {
    get("/streams/manifest") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        when (val result = manifestService.masterPlaylist(url)) {
            is ExtractionResult.Success -> call.respondText(result.data, ContentType.parse("application/vnd.apple.mpegurl"))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }

    get("/streams/manifest/media") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val index = call.request.queryParameters["index"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'index' parameter"))
        val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 3600L

        when (val result = manifestService.mediaPlaylist(url, index, duration)) {
            is ExtractionResult.Success -> call.respondText(result.data, ContentType.parse("application/vnd.apple.mpegurl"))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }

    get("/streams/manifest/audio") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val duration = call.request.queryParameters["duration"]?.toLongOrNull() ?: 3600L

        when (val result = manifestService.audioPlaylist(url, duration)) {
            is ExtractionResult.Success -> call.respondText(result.data, ContentType.parse("application/vnd.apple.mpegurl"))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
