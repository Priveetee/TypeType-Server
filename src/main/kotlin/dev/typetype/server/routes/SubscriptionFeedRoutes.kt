package dev.typetype.server.routes

import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.TokenService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.subscriptionFeedRoutes(feedService: SubscriptionFeedService, tokenService: TokenService) {
    get("/subscriptions/feed") {
        call.withAuth(tokenService) {
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
            call.respond(feedService.getFeed(tokenService.getOrGenerate(), page, limit))
        }
    }
}
