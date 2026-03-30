package dev.typetype.server.routes

import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionShortsFeedService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_SHORTS_FEED_PAGE = 10_000

fun Route.subscriptionShortsFeedRoutes(feedService: SubscriptionShortsFeedService, authService: AuthService) {
    get("/subscriptions/shorts") {
        call.withJwtAuth(authService) { userId ->
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceIn(0, MAX_SHORTS_FEED_PAGE) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
            call.respond(feedService.getFeed(userId, page, limit))
        }
    }
}
