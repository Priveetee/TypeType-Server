package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.streamRoutes(streamService: StreamService) {
    get("/streams") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing 'url' parameter")

        when (val result = streamService.getStreamInfo(url)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, result.message)
        }
    }
}
