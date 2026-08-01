package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.YouTubeSubtitleContentResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes

internal suspend fun ApplicationCall.respondYouTubeSubtitle(result: YouTubeSubtitleContentResult) {
    when (result) {
        is YouTubeSubtitleContentResult.Ready -> {
            response.headers.append("Cache-Control", "private, max-age=300", safeOnly = false)
            respondBytes(result.content, ContentType.parse("text/vtt; charset=utf-8"))
        }
        YouTubeSubtitleContentResult.InvalidRequest -> respondSubtitleError(
            HttpStatusCode.BadRequest,
            "Invalid YouTube subtitle URL",
            "subtitle_request_invalid",
        )
        YouTubeSubtitleContentResult.NotFound -> respondSubtitleError(
            HttpStatusCode.NotFound,
            "Subtitle track not found",
            "subtitle_track_not_found",
        )
        YouTubeSubtitleContentResult.Throttled -> respondSubtitleError(
            HttpStatusCode.TooManyRequests,
            "YouTube temporarily throttled subtitle retrieval",
            "subtitle_upstream_throttled",
        )
        YouTubeSubtitleContentResult.InvalidPayload -> respondSubtitleError(
            HttpStatusCode.BadGateway,
            "YouTube returned invalid subtitle content",
            "subtitle_payload_invalid",
        )
        YouTubeSubtitleContentResult.Unavailable -> respondSubtitleError(
            HttpStatusCode.BadGateway,
            "YouTube subtitle retrieval failed",
            "subtitle_upstream_unavailable",
        )
    }
}

private suspend fun ApplicationCall.respondSubtitleError(
    status: HttpStatusCode,
    message: String,
    code: String,
) {
    respond(status, ErrorResponse(message, code))
}
