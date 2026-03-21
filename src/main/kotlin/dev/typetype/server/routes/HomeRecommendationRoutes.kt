package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HomeRecommendationCursorCodec
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.YOUTUBE_SERVICE_ID
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_RECOMMENDATION_LIMIT = 60

fun Route.homeRecommendationRoutes(recommendationService: HomeRecommendationService, authService: AuthService) {
    get("/recommendations/home") {
        call.withJwtAuth(authService) { userId ->
            val serviceId = call.request.queryParameters["service"]?.toIntOrNull() ?: YOUTUBE_SERVICE_ID
            if (serviceId !in VALID_SERVICE_IDS) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_RECOMMENDATION_LIMIT)
                ?: 20
            val rawCursor = call.request.queryParameters["cursor"]
            val cursor = HomeRecommendationCursorCodec.decode(rawCursor)
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid 'cursor' parameter"),
                )
            call.respond(
                recommendationService.getHome(
                    userId = userId,
                    serviceId = serviceId,
                    limit = limit,
                    cursor = cursor,
                ),
            )
        }
    }
}
