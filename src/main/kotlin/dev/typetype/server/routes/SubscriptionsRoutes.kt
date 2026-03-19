package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

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
    delete("/subscriptions/{channelUrl...}") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.parameters.getAll("channelUrl")?.joinToString("/") ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            val deleted = subscriptionsService.delete(userId, channelUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
