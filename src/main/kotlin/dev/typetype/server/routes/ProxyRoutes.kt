package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.YouTubeSubtitleService
import dev.typetype.server.services.isYouTubeTimedTextUrl
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.proxyRoutes(
    proxyService: ProxyService,
    youtubeSubtitleService: YouTubeSubtitleService? = null,
) {
    get("/proxy") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        if (youtubeSubtitleService != null && isYouTubeTimedTextUrl(url)) {
            return@get call.respondYouTubeSubtitle(youtubeSubtitleService.fetchSubtitleContent(url))
        }

        val rangeHeader = call.request.headers["Range"]
        val domandBid = call.request.queryParameters["domand_bid"]

        call.respondProxyResult(proxyService.pipe(url, rangeHeader, domandBid))
    }
}
