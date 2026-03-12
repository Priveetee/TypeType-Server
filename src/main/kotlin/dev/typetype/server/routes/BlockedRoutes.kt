package dev.typetype.server.routes

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.BlockedService
import dev.typetype.server.services.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.blockedRoutes(blockedService: BlockedService, tokenService: TokenService) {
    get("/blocked/channels") {
        call.withAuth(tokenService) { call.respond(blockedService.getChannels()) }
    }
    post("/blocked/channels") {
        call.withAuth(tokenService) {
            val item = runCatching { call.receive<BlockedItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, blockedService.addChannel(item.url, item.name, item.thumbnailUrl))
        }
    }
    delete("/blocked/channels/{channelUrl}") {
        call.withAuth(tokenService) {
            val channelUrl = call.parameters["channelUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            val deleted = blockedService.deleteChannel(channelUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    get("/blocked/videos") {
        call.withAuth(tokenService) { call.respond(blockedService.getVideos()) }
    }
    post("/blocked/videos") {
        call.withAuth(tokenService) {
            val item = runCatching { call.receive<BlockedItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, blockedService.addVideo(item.url))
        }
    }
    delete("/blocked/videos/{videoUrl}") {
        call.withAuth(tokenService) {
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = blockedService.deleteVideo(videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
