package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionFeedPreparingResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionFeedPageResult
import dev.typetype.server.services.SubscriptionFeedService
import dev.typetype.server.services.SubscriptionFeedVisibility
import dev.typetype.server.services.SettingsService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val MAX_FEED_PAGE = 10_000

fun Route.subscriptionFeedRoutes(
    feedService: SubscriptionFeedService,
    authService: AuthService,
    settingsService: SettingsService? = null,
) {
    get("/subscriptions/feed") {
        call.withJwtAuth(authService) { userId ->
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceIn(0, MAX_FEED_PAGE) ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
            val cursor = call.request.queryParameters["cursor"]
            val visibility = settingsService?.subscriptionFeedVisibility(userId) ?: SubscriptionFeedVisibility()
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            when (
                val result = feedService.getPage(
                    userId,
                    page,
                    limit,
                    cursor,
                    visibility.hideLiveStreams,
                    visibility.hideMembersOnlyContent,
                )
            ) {
                is SubscriptionFeedPageResult.Ready -> call.respond(result.response)
                is SubscriptionFeedPageResult.Preparing -> {
                    call.response.headers.append(HttpHeaders.RetryAfter, "1")
                    call.respond(
                        HttpStatusCode.Accepted,
                        SubscriptionFeedPreparingResponse(retryAfterMs = result.retryAfterMs),
                    )
                }
                SubscriptionFeedPageResult.InvalidCursor -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid subscription feed cursor", "subscription_feed_invalid_cursor"),
                )
                SubscriptionFeedPageResult.StaleGeneration -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Subscription feed generation is no longer available", "subscription_feed_stale_generation"),
                )
            }
        }
    }
}
