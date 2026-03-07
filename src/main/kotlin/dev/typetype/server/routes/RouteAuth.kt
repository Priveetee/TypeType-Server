package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.withAuth(token: String, block: suspend () -> Unit) {
    val header = request.headers["X-Instance-Token"]
    if (header == null || header != token) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or missing X-Instance-Token"))
        return
    }
    block()
}
