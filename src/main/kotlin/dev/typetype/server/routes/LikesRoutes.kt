package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.LikesService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.likesRoutes(likesService: LikesService, token: String) {
    get("/likes") {
        call.withAuth(token) { call.respond(likesService.getAll()) }
    }
    post("/likes/{videoUrl}") {
        call.withAuth(token) {
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            call.respond(HttpStatusCode.Created, likesService.add(videoUrl))
        }
    }
    delete("/likes/{videoUrl}") {
        call.withAuth(token) {
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = likesService.delete(videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
