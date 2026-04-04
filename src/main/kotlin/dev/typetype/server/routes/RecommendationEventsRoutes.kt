package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RecommendationEventRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.recommendationEventsRoutes(eventService: RecommendationEventService, authService: AuthService) {
    get("/recommendations/events") {
        call.withJwtAuth(authService) { userId ->
            call.respond(eventService.getAll(userId))
        }
    }
    post("/recommendations/events") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<RecommendationEventRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (request.eventType !in setOf("impression", "click", "watch", "favorite", "watch_later_add", "short_skip")) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid eventType"))
            }
            if ((request.eventType == "click" || request.eventType == "watch") && request.videoUrl.isNullOrBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            }
            if (request.watchRatio != null && (request.watchRatio < 0.0 || request.watchRatio > 1.0)) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid watchRatio"))
            }
            call.respond(
                HttpStatusCode.Created,
                eventService.add(
                    userId = userId,
                    eventType = request.eventType,
                    videoUrl = request.videoUrl,
                    uploaderUrl = request.uploaderUrl,
                    title = request.title,
                    watchRatio = request.watchRatio,
                ),
            )
        }
    }
}
