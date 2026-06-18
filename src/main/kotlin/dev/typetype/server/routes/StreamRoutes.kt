package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_RECONNECT_ERROR
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val AUTHENTICATED_STREAMS_CACHE_CONTROL = "no-store"

fun Route.streamRoutes(
    streamService: StreamService,
    authService: AuthService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
): Unit {
    get("/streams") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val publicResult = streamService.getStreamInfo(url)
        val userId = authService?.let { call.optionalJwtUserId(it) }
        val sessionResult = if (
            userId != null &&
            youtubeSessionStreamInfo != null &&
            publicResult.shouldTryYoutubeSession()
        ) {
            youtubeSessionStreamInfo(userId, url)
        } else {
            null
        }
        val usedYoutubeSession = sessionResult != null
        when (val result = publicResult.resolveWith(sessionResult)) {
            is ExtractionResult.Success -> {
                call.response.headers.append(
                    HttpHeaders.CacheControl,
                    if (usedYoutubeSession) AUTHENTICATED_STREAMS_CACHE_CONTROL else STREAMS_CACHE_CONTROL,
                )
                if (usedYoutubeSession) {
                    SignedHlsManifestCookie.tokenFromPath(result.data.hlsUrl)
                        ?.let { SignedHlsManifestCookie.append(call.response, url, it) }
                }
                call.respond(result.data)
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, result.toErrorResponse())
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

private fun ExtractionResult<StreamResponse>.shouldTryYoutubeSession(): Boolean = when (this) {
    is ExtractionResult.Success -> data.hlsUrl.isBlank()
    is ExtractionResult.BadRequest -> true
    is ExtractionResult.Failure -> true
}

private fun ExtractionResult<StreamResponse>.resolveWith(
    sessionResult: ExtractionResult<StreamResponse>?,
): ExtractionResult<StreamResponse> {
    if (sessionResult == null) return this
    if (this is ExtractionResult.Success && sessionResult is ExtractionResult.Success) {
        return ExtractionResult.Success(data.mergeWithSession(sessionResult.data))
    }
    if (this is ExtractionResult.Success) return this
    return sessionResult
}

private fun StreamResponse.mergeWithSession(session: StreamResponse): StreamResponse {
    val base = if (session.playableStreamCount() > playableStreamCount()) session else this
    return base.copy(
        hlsUrl = base.hlsUrl.ifBlank { session.hlsUrl.ifBlank { hlsUrl } },
        dashMpdUrl = base.dashMpdUrl.ifBlank { session.dashMpdUrl.ifBlank { dashMpdUrl } },
    )
}

private fun StreamResponse.playableStreamCount(): Int =
    videoStreams.size + videoOnlyStreams.size + audioStreams.size

private fun ExtractionResult.BadRequest.toErrorResponse(): ErrorResponse =
    if (message == YOUTUBE_SESSION_RECONNECT_ERROR) {
        ErrorResponse(message, "youtube_session_needs_reconnect")
    } else {
        ErrorResponse(message)
    }
