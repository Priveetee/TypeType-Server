package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SearchHistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get

fun Route.searchHistoryRoutes(searchHistoryService: SearchHistoryService, token: String) {
    get("/search-history") {
        call.withAuth(token) { call.respond(searchHistoryService.getAll()) }
    }
    delete("/search-history") {
        call.withAuth(token) {
            searchHistoryService.deleteAll()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
