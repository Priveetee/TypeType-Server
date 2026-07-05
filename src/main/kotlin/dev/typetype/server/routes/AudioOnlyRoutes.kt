package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AudioOnlyMediaTokenResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AudioOnlyStreamKind
import dev.typetype.server.services.AudioOnlyStreamResolver
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProxyService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head

fun Route.audioOnlyContractRoutes(
    streamService: StreamService,
    tokenService: AudioOnlyMediaTokenService,
    authService: AuthService? = null,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
    proxyService: ProxyService? = null,
) {
    get("/streams/audio-only") {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return@get
        val preferOriginal = call.request.queryParameters["preferOriginal"].toBooleanParam()
        val preferredLocale = call.request.queryParameters["preferredLocale"]?.takeIf { it.isNotBlank() }
        val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
        when (val result = resolver.resolve(
            url,
            access.userId,
            preferOriginal,
            preferredLocale,
            progressivePlayable = { stream -> proxyService?.isPlayableProgressiveAudio(stream) ?: true },
        )) {
            is ExtractionResult.Success -> {
                if (!access.profile.allowsUploader(result.data.response.uploaderUrl, result.data.response.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                when (val response = result.data.toResponse(
                    tokenService,
                    publicHlsManifestTokenService,
                    access.userId,
                    url,
                    preferOriginal,
                    preferredLocale,
                )) {
                    is ExtractionResult.Success -> call.respond(response.data)
                    is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(response.message))
                    is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(response.message))
                }
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

internal fun Route.audioOnlySourceRoutes(
    streamService: StreamService,
    proxyService: ProxyService,
    tokenService: AudioOnlyMediaTokenService,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)? = null,
    sabrSessionStore: SabrSessionStore? = null,
) {
    head("/streams/audio-only/source") {
        val result = call.resolveAudioOnlySource(tokenService, streamService, youtubeSessionStreamInfo, sabrSessionStore)
            ?: return@head
        call.respondAudioOnlyHead(result)
    }
    get("/streams/audio-only/source") {
        val source = call.resolveAudioOnlySource(tokenService, streamService, youtubeSessionStreamInfo, sabrSessionStore)
            ?: return@get
        when (val result = source.result) {
            is ExtractionResult.Success -> when (result.data.kind) {
                AudioOnlyStreamKind.SabrProgressive -> {
                    val store = sabrSessionStore ?: return@get call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("No audio-only stream is available"),
                    )
                    call.respondSabrAudioOnlySource(store, source.token, result.data)
                }
                else -> call.respondProxyResult(
                    proxyService.pipe(result.data.stream.url, call.request.headers.audioOnlyRangeHeader(), null)
                        .ensureProgressiveAudio()
                )
            }
            is ExtractionResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
            is ExtractionResult.Failure -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
        }
    }
}

private suspend fun ApplicationCall.resolveAudioOnlySource(
    tokenService: AudioOnlyMediaTokenService,
    streamService: StreamService,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
    sabrSessionStore: SabrSessionStore?,
): AudioOnlySourceResolution? {
    val raw = request.queryParameters["token"]
        ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'token' parameter")).let { null }
    val token = when (val verified = tokenService.verify(raw)) {
        is AudioOnlyMediaTokenResult.Valid -> verified.token
        AudioOnlyMediaTokenResult.Expired -> return respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Audio-only token expired"),
        ).let { null }
        AudioOnlyMediaTokenResult.Invalid -> return respond(
            HttpStatusCode.Unauthorized,
            ErrorResponse("Invalid audio-only token"),
        ).let { null }
    }
    val resolver = AudioOnlyStreamResolver(streamService, youtubeSessionStreamInfo)
    return AudioOnlySourceResolution(
        token,
        resolver.resolve(
            token.videoUrl,
            token.userId,
            token.preferOriginal,
            token.preferredLocale,
            allowHls = false,
            allowSabr = sabrSessionStore != null,
            selectedItag = token.selectedItag,
            selectedAudioTrackId = token.selectedAudioTrackId,
        ),
    )
}

private suspend fun ApplicationCall.respondAudioOnlyHead(source: AudioOnlySourceResolution): Unit {
    when (val result = source.result) {
        is ExtractionResult.Success -> {
            response.headers.append(HttpHeaders.CacheControl, "no-store")
            response.headers.append(HttpHeaders.ContentType, result.data.stream.mimeType)
            val length = result.data.stream.contentLength.takeIf { it > 0 && result.data.kind == AudioOnlyStreamKind.Progressive }
            val range = request.headers[HttpHeaders.Range]?.let { parseSingleByteRange(it, length) }
            if (range != null && length != null) {
                response.headers.append(HttpHeaders.AcceptRanges, "bytes")
                response.headers.append(HttpHeaders.ContentLength, (range.last - range.first + 1L).toString())
                response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/$length")
                respond(HttpStatusCode.PartialContent)
            } else {
                response.headers.append(HttpHeaders.AcceptRanges, if (length == null) "none" else "bytes")
                length?.let { response.headers.append(HttpHeaders.ContentLength, it.toString()) }
                respond(HttpStatusCode.OK)
            }
        }
        is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
        is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
    }
}

private fun parseSingleByteRange(value: String, length: Long?): LongRange? {
    val total = length ?: return null
    if (!value.startsWith("bytes=")) return null
    val parts = value.removePrefix("bytes=").split("-", limit = 2)
    if (parts.size != 2) return null
    val start = parts[0].toLongOrNull()?.coerceAtLeast(0L) ?: return null
    val end = parts[1].toLongOrNull()?.coerceAtMost(total - 1L) ?: total - 1L
    if (start > end || start >= total) return null
    return start..end
}

private fun String?.toBooleanParam(): Boolean = equals("true", ignoreCase = true)
