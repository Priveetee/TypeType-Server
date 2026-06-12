package dev.typetype.server.routes

import dev.typetype.server.models.ChannelPageRequest
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ChannelService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.channelRoutes(channelService: ChannelService) {
    get("/channel") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val sort = call.request.queryParameters["sort"]?.takeIf { it.isNotBlank() }

        when (val result = channelService.getChannel(url = url, nextpage = nextpage, sort = sort)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
    post("/channel/page") {
        val request = call.receive<ChannelPageRequest>()
        val url = request.url?.takeIf { it.isNotBlank() }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        call.respondChannelResult(
            channelService.getChannel(
                url = url,
                nextpage = request.nextpage?.takeIf { it.isNotBlank() },
                sort = request.sort?.takeIf { it.isNotBlank() },
            )
        )
    }
}

private suspend fun ApplicationCall.respondChannelResult(result: ExtractionResult<ChannelResponse>) {
    when (result) {
        is ExtractionResult.Success -> respond(result.data)
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}
