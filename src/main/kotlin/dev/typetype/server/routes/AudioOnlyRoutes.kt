package dev.typetype.server.routes

import dev.typetype.server.models.AudioOnlyStreamResponse
import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AudioOnlyStreamResolver
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun Route.audioOnlyContractRoutes(
    streamService: StreamService,
    tokenService: AudioOnlyMediaTokenService,
    authService: AuthService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/streams/audio-only") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val preferOriginal = call.request.queryParameters["preferOriginal"].toBooleanParam()
        val preferredLocale = call.request.queryParameters["preferredLocale"]?.takeIf { it.isNotBlank() }
        val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
        when (val result = resolver.resolve(url, access.userId, preferOriginal, preferredLocale)) {
            is ExtractionResult.Success -> {
                if (!access.profile.allowsUploader(result.data.response.uploaderUrl, result.data.response.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respond(result.data.toResponse(tokenService, access.userId, url, preferOriginal, preferredLocale))
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

fun Route.audioOnlySourceRoutes(
    streamService: StreamService,
    proxyService: ProxyService,
    tokenService: AudioOnlyMediaTokenService,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
) {
    get("/streams/audio-only/source") {
        val raw = call.request.queryParameters["token"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'token' parameter"))
        val token = when (val verified = tokenService.verify(raw)) {
            is AudioOnlyMediaTokenResult.Valid -> verified.token
            AudioOnlyMediaTokenResult.Expired -> return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Audio-only token expired"))
            AudioOnlyMediaTokenResult.Invalid -> return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid audio-only token"))
        }
        val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
        when (val result = resolver.resolve(token.videoUrl, token.userId, token.preferOriginal, token.preferredLocale)) {
            is ExtractionResult.Success -> call.respondProxyResult(
                proxyService.pipe(result.data.stream.url, call.request.headers.audioOnlyRangeHeader(), null)
                    .ensureProgressiveAudio()
            )
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

private fun AudioOnlyStreamSelection.toResponse(
    tokenService: AudioOnlyMediaTokenService,
    userId: String?,
    url: String,
    preferOriginal: Boolean,
    preferredLocale: String?,
): AudioOnlyStreamResponse {
    val token = tokenService.createToken(userId, url, preferOriginal, preferredLocale)
    return AudioOnlyStreamResponse(
        src = "/streams/audio-only/source?token=${encode(token)}",
        mimeType = stream.mimeType,
        codec = stream.codec,
        bitrate = stream.bitrate,
        contentLength = stream.contentLength.takeIf { it > 0 },
        duration = response.duration.takeIf { it > 0 },
    )
}

private fun String?.toBooleanParam(): Boolean = equals("true", ignoreCase = true)

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
