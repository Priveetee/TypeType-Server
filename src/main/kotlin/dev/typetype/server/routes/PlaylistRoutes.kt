package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.services.PlaylistService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.playlistRoutes(playlistService: PlaylistService, token: String) {
    get("/playlists") {
        call.withAuth(token) { call.respond(playlistService.getAll()) }
    }
    post("/playlists") {
        call.withAuth(token) {
            val item = runCatching { call.receive<PlaylistItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, playlistService.create(item))
        }
    }
    get("/playlists/{id}") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val playlist = playlistService.getById(id) ?: return@withAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            call.respond(playlist)
        }
    }
    put("/playlists/{id}") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val item = runCatching { call.receive<PlaylistItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val updated = playlistService.update(id, item)
            if (updated) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    delete("/playlists/{id}") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = playlistService.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    post("/playlists/{id}/videos") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val video = runCatching { call.receive<PlaylistVideoItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, playlistService.addVideo(id, video))
        }
    }
    delete("/playlists/{id}/videos/{videoUrl}") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = playlistService.removeVideo(id, videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
