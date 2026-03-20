package dev.typetype.server.routes

import dev.typetype.server.services.AvatarService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.net.HttpURLConnection
import java.net.URI

fun Route.avatarRoutes(avatarService: AvatarService) {
    get("/avatar/openmoji/{code}.svg") {
        val rawCode = call.parameters["code"]
        val code = rawCode?.let(avatarService::normalizeEmojiCode)
        if (code == null) return@get call.respondText("Invalid OpenMoji code", status = HttpStatusCode.BadRequest)
        val connection = URI.create(avatarService.openMojiCdnUrl(code)).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (connection.responseCode != 200) return@get call.respondText("Avatar not found", status = HttpStatusCode.NotFound)
        val bytes = connection.inputStream.use { it.readBytes() }
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondBytes(bytes, ContentType.parse("image/svg+xml"))
    }
}
