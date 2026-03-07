package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.SearchService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.searchRoutes(searchService: SearchService) {
    get("/search") {
        val query = call.request.queryParameters["q"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing 'q' parameter")
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid 'service' parameter")

        when (val result = searchService.search(query = query, serviceId = serviceId)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, result.message)
        }
    }
}
