package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SearchHistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchHistoryBody(val term: String)

fun Route.searchHistoryRoutes(searchHistoryService: SearchHistoryService, token: String) {
    get("/search-history") {
        call.withAuth(token) { call.respond(searchHistoryService.getAll()) }
    }
    post("/search-history") {
        call.withAuth(token) {
            val body = runCatching { call.receive<SearchHistoryBody>() }.getOrElse {
                return@withAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            call.respond(HttpStatusCode.Created, searchHistoryService.add(body.term))
        }
    }
    delete("/search-history") {
        call.withAuth(token) {
            searchHistoryService.deleteAll()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
