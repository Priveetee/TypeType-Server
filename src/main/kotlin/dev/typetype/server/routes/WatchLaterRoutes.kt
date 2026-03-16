package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.services.TokenService
import dev.typetype.server.services.WatchLaterService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.watchLaterRoutes(watchLaterService: WatchLaterService, tokenService: TokenService) {
    get("/watch-later") {
        call.withAuth(tokenService) { call.respond(watchLaterService.getAll()) }
    }
    post("/watch-later") {
        call.withAuth(tokenService) {
            val item = runCatching { call.receive<WatchLaterItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, watchLaterService.add(item))
        }
    }
    delete("/watch-later/{videoUrl...}") {
        call.withAuth(tokenService) {
            val videoUrl = call.parameters.getAll("videoUrl")?.joinToString("/") ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val deleted = watchLaterService.delete(videoUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
