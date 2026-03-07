package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.services.HistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.historyRoutes(historyService: HistoryService, token: String) {
    get("/history") {
        call.withAuth(token) { call.respond(historyService.getAll()) }
    }
    post("/history") {
        call.withAuth(token) {
            val item = runCatching { call.receive<HistoryItem>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, historyService.add(item))
        }
    }
    delete("/history/{id}") {
        call.withAuth(token) {
            val id = call.parameters["id"] ?: return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
            val deleted = historyService.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent) else call.respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        }
    }
    delete("/history") {
        call.withAuth(token) {
            historyService.deleteAll()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
