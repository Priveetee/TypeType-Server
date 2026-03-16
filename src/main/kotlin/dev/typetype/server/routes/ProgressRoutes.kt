package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
internal data class ProgressBody(val position: Long)

fun Route.progressRoutes(progressService: ProgressService, tokenService: TokenService) {
    get("/progress/{videoUrl...}") {
        call.withAuth(tokenService) {
            val videoUrl = call.parameters.getAll("videoUrl")?.joinToString("/") ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val item = progressService.get(videoUrl) ?: return@withAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            call.respond(item)
        }
    }
    put("/progress/{videoUrl...}") {
        call.withAuth(tokenService) {
            val videoUrl = call.parameters.getAll("videoUrl")?.joinToString("/") ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val body = runCatching { call.receive<ProgressBody>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(progressService.upsert(videoUrl, body.position))
        }
    }
}
