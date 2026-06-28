package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_RECONNECT_ERROR
import dev.typetype.server.services.filterAllowed
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
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
): Unit {
    get("/streams") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val userId = access.userId
        val sessionResult = if (
            userId != null &&
            youtubeSessionStreamInfo != null
        ) {
            youtubeSessionStreamInfo(userId, url)
        } else {
            null
        }
        val publicResult = if (sessionResult.hasPlayableStreams()) null else streamService.getStreamInfo(url)
        val usedYoutubeSession = sessionResult != null
        val accessProfile = access.profile
        when (val result = publicResult.resolveWith(sessionResult)) {
            is ExtractionResult.Success -> {
                if (!accessProfile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                val data = result.data.filterAllowed(accessProfile)
                call.response.headers.append(
                    HttpHeaders.CacheControl,
                    if (usedYoutubeSession || accessProfile.enabled) AUTHENTICATED_STREAMS_CACHE_CONTROL else STREAMS_CACHE_CONTROL,
                )
                if (usedYoutubeSession) {
                    SignedHlsManifestCookie.tokenFromPath(data.hlsUrl)
                        ?.let { SignedHlsManifestCookie.append(call.response, url, it) }
                }
                call.respond(data)
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, result.toErrorResponse())
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

private fun ExtractionResult<StreamResponse>?.resolveWith(
    sessionResult: ExtractionResult<StreamResponse>?,
): ExtractionResult<StreamResponse> {
    if (this == null && sessionResult != null) return sessionResult
    if (this == null) return ExtractionResult.Failure("Stream extraction failed")
    if (sessionResult == null) return this
    if (this is ExtractionResult.Success && sessionResult is ExtractionResult.Success) {
        return ExtractionResult.Success(sessionResult.data.mergeWithPublic(data))
    }
    if (this is ExtractionResult.Success) return this
    return sessionResult
}

private fun StreamResponse.mergeWithPublic(public: StreamResponse): StreamResponse =
    copy(
        hlsUrl = hlsUrl.ifBlank { public.hlsUrl },
        dashMpdUrl = dashMpdUrl.ifBlank { public.dashMpdUrl },
        videoStreams = videoStreams.ifEmpty { public.videoStreams },
        videoOnlyStreams = videoOnlyStreams.ifEmpty { public.videoOnlyStreams },
        audioStreams = audioStreams.ifEmpty { public.audioStreams },
    )

private fun ExtractionResult<StreamResponse>?.hasPlayableStreams(): Boolean =
    this is ExtractionResult.Success && data.playableStreamCount() > 0

private fun StreamResponse.playableStreamCount(): Int =
    videoStreams.size + videoOnlyStreams.size + audioStreams.size

private fun ExtractionResult.BadRequest.toErrorResponse(): ErrorResponse =
    if (message == YOUTUBE_SESSION_RECONNECT_ERROR) {
        ErrorResponse(message, "youtube_session_needs_reconnect")
    } else {
        ErrorResponse(message)
    }
