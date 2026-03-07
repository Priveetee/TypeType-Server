package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.SubscriptionsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.subscriptionsRoutes(subscriptionsService: SubscriptionsService, token: String) {
    get("/subscriptions") {
        call.withAuth(token) { call.respond(subscriptionsService.getAll()) }
    }
    post("/subscriptions") {
        call.withAuth(token) {
            val item = runCatching { call.receive<SubscriptionItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, subscriptionsService.add(item))
        }
    }
    delete("/subscriptions/{channelUrl}") {
        call.withAuth(token) {
            val channelUrl = call.parameters["channelUrl"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channelUrl"))
            val deleted = subscriptionsService.delete(channelUrl)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
}
