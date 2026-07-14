package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ProxyService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.proxyRoutes(proxyService: ProxyService) {
    get("/proxy") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val rangeHeader = call.request.headers["Range"]
        val domandBid = call.request.queryParameters["domand_bid"]

        call.respondProxyResult(proxyService.pipe(url, rangeHeader, domandBid))
    }
}
