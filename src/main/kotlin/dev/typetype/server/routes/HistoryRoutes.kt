package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private const val MAX_HISTORY_LIMIT = 1000

fun Route.historyRoutes(historyService: HistoryService, authService: AuthService) {
    get("/history") {
        call.withJwtAuth(authService) { userId ->
            val q = call.request.queryParameters["q"]
            val from = call.request.queryParameters["from"]?.toLongOrNull()
            val to = call.request.queryParameters["to"]?.toLongOrNull()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_HISTORY_LIMIT) ?: 60
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val (items, total) = historyService.search(userId, q, from, to, limit, offset)
            call.response.headers.append("X-Total-Count", total.toString())
            call.respond(items)
        }
    }
    post("/history") {
        call.withJwtAuth(authService) { userId ->
            val item = runCatching { call.receive<HistoryItem>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, historyService.add(userId, item))
        }
    }
    delete("/history/{id}") {
        call.withJwtAuth(authService) { userId ->
            val id = call.parameters["id"] ?: return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = historyService.delete(userId, id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    delete("/history") {
        call.withJwtAuth(authService) { userId ->
            historyService.deleteAll(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
