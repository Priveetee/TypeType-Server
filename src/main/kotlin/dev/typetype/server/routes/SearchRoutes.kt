package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.filterAllowed
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.searchRoutes(
    searchService: SearchService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/search/filters") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
        when (val result = searchService.filters(serviceId = serviceId)) {
            is ExtractionResult.Success -> call.respond(result.data)
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
    get("/search") {
        val query = call.request.queryParameters["q"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'q' parameter"))
        val serviceId = call.request.queryParameters["service"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing or invalid 'service' parameter"))
        if (serviceId !in VALID_SERVICE_IDS)
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid 'service' parameter"))
        val nextpage = call.request.queryParameters["nextpage"]
        val contentFilter = call.request.queryParameters["contentFilter"]
        val sortFilter = call.request.queryParameters["sortFilter"]

        val profile = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService)?.profile ?: return@get
        when (val result = searchService.search(query = query, serviceId = serviceId, nextpage = nextpage, contentFilter = contentFilter, sortFilter = sortFilter)) {
            is ExtractionResult.Success -> call.respond(result.data.filterAllowed(profile))
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}
