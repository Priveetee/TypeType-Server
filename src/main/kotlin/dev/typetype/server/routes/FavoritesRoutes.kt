package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.FavoritesService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.favoritesRoutes(favoritesService: FavoritesService, token: String) {
    get("/favorites") {
        call.withAuth(token) { call.respond(favoritesService.getAll()) }
    }
    post("/favorites/{videoUrl}") {
        call.withAuth(token) {
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            call.respond(HttpStatusCode.Created, favoritesService.add(videoUrl))
        }
    }
    delete("/favorites/{videoUrl}") {
        call.withAuth(token) {
            val videoUrl = call.parameters["videoUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = favoritesService.delete(videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
