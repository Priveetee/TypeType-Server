package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ProgressItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ProgressService
import dev.typetype.server.services.SettingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
internal data class ProgressBody(val position: Long)

fun Route.progressRoutes(progressService: ProgressService, authService: AuthService, settingsService: SettingsService? = null) {
    get("/progress/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val item = progressService.get(userId, videoUrl) ?: return@withJwtAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            call.respond(item)
        }
    }
    get("/progress") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val item = progressService.get(userId, videoUrl) ?: return@withJwtAuth call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
            call.respond(item)
        }
    }
    put("/progress/{videoUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.urlTailParameter("videoUrl") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val body = runCatching { call.receive<ProgressBody>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (settingsService?.isWatchHistoryDisabled(userId) == true) {
                return@withJwtAuth call.respond(skippedProgress(videoUrl, body.position))
            }
            call.respond(progressService.upsert(userId, videoUrl, body.position))
        }
    }
    put("/progress") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            val body = runCatching { call.receive<ProgressBody>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (settingsService?.isWatchHistoryDisabled(userId) == true) {
                return@withJwtAuth call.respond(skippedProgress(videoUrl, body.position))
            }
            call.respond(progressService.upsert(userId, videoUrl, body.position))
        }
    }
}

private fun skippedProgress(videoUrl: String, position: Long): ProgressItem = ProgressItem(
    videoUrl = videoUrl,
    position = position.coerceAtLeast(0L),
    updatedAt = System.currentTimeMillis(),
)
