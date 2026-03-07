package dev.typetype.server.routes

import dev.typetype.server.services.TokenService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
internal data class TokenResponse(val token: String)

fun Route.tokenRoutes(tokenService: TokenService) {
    get("/token") {
        call.respond(TokenResponse(tokenService.getOrGenerate()))
    }
}
