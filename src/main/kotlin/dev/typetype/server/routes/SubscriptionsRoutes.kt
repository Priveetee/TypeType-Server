package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun Route.subscriptionsRoutes(subscriptionsService: SubscriptionsService, authService: AuthService) {
    get("/subscriptions") {
        call.withJwtAuth(authService) { userId -> call.respond(subscriptionsService.getAll(userId)) }
    }
    post("/subscriptions") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<SubscriptionItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, subscriptionsService.add(userId, item))
        }
    }
    delete("/subscriptions") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.request.queryParameters["url"]?.takeIf { it.isNotBlank() }
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            call.respondDeleteResult(subscriptionsService, userId, channelUrl)
        }
    }
    delete("/subscriptions/{channelUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.extractDeleteChannelUrl()
                ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            call.respondDeleteResult(subscriptionsService, userId, channelUrl)
        }
    }
}

private suspend fun ApplicationCall.respondDeleteResult(
    subscriptionsService: SubscriptionsService,
    userId: String,
    channelUrl: String,
) {
    val deleted = subscriptionsService.delete(userId, channelUrl)
    if (deleted) respond(HttpStatusCode.NoContent) else respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
}

private fun ApplicationCall.extractDeleteChannelUrl(): String? {
    val queryUrl = request.queryParameters["url"]?.takeIf { it.isNotBlank() }
    if (queryUrl != null) return queryUrl
    val rawPath = request.path()
    val marker = "/subscriptions/"
    val index = rawPath.indexOf(marker)
    if (index == -1) return null
    val rawTail = rawPath.substring(index + marker.length)
    if (rawTail.isBlank()) return null
    return runCatching { URLDecoder.decode(rawTail, StandardCharsets.UTF_8) }
        .getOrDefault(rawTail)
        .takeIf { it.isNotBlank() }
}
