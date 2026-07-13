package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.SignedHlsManifestCookie
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_RECONNECT_ERROR
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.withSabrManifestUrls
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
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
    legacyStreamService: StreamService = streamService,
    nicoNicoStreamService: StreamService = legacyStreamService,
    bilibiliStreamService: StreamService = legacyStreamService,
    sabrBootstrapStreamService: StreamService = streamService,
    sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse)? = null,
): Unit {
    val dependencies = StreamRouteDependencies(
        authService = authService,
        youtubeSessionStreamInfo = youtubeSessionStreamInfo,
        accessControlService = accessControlService,
        adminSettingsService = adminSettingsService,
        publicHlsManifestTokenService = publicHlsManifestTokenService,
        sabrStreamContractFilter = sabrStreamContractFilter,
    )
    listOf("/streams", "/streams/youtube/sabr").forEach {
        streamRoute(it, StreamDeliveryMode.YoutubeSabr, streamService, dependencies)
    }
    streamRoute(
        "/streams/youtube/sabr/bootstrap",
        StreamDeliveryMode.YoutubeSabr,
        sabrBootstrapStreamService,
        dependencies.copy(youtubeSessionStreamInfo = null),
    )
    listOf("/streams/legacy", "/streams/youtube/legacy").forEach {
        streamRoute(it, StreamDeliveryMode.YoutubeLegacy, legacyStreamService, dependencies)
    }
    streamRoute("/streams/niconico", StreamDeliveryMode.NicoNico, nicoNicoStreamService, dependencies)
    streamRoute("/streams/bilibili", StreamDeliveryMode.BiliBili, bilibiliStreamService, dependencies)
}

private fun Route.streamRoute(
    path: String,
    deliveryMode: StreamDeliveryMode,
    streamService: StreamService,
    dependencies: StreamRouteDependencies,
): Unit {
    get(path) {
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))
        if (!deliveryMode.accepts(url)) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("URL does not match stream endpoint", "provider_mismatch"),
            )
        }

        val access = call.accessProfileOrRespond(
            dependencies.authService,
            dependencies.accessControlService,
            dependencies.adminSettingsService,
        ) ?: return@get
        val userId = access.userId
        val sessionResult = fetchYoutubeSession(userId, url, deliveryMode, dependencies.youtubeSessionStreamInfo)
            .forDeliveryMode(deliveryMode)
        val publicResult = if (sessionResult.hasPlayableSource()) {
            null
        } else {
            streamService.getStreamInfo(url)
        }
        val usedYoutubeSession = sessionResult != null
        val accessProfile = access.profile
        when (val result = publicResult.resolveWith(sessionResult)) {
            is ExtractionResult.Success -> {
                if (!accessProfile.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                    return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                }
                val selected = if (deliveryMode.isSabr()) {
                    result.data.withSabrManifestUrls().onlySabrStreams()
                } else {
                    result.data.withoutSabrStreams()
                }
                val filtered = selected
                    .filterAllowed(accessProfile)
                    .withSignedPublicHlsUrl(
                        userId != null && !access.allowGuest,
                        dependencies.publicHlsManifestTokenService,
                    )
                val data = if (!deliveryMode.isSabr() || dependencies.sabrStreamContractFilter == null) {
                    filtered
                } else {
                    dependencies.sabrStreamContractFilter.invoke(url, filtered)
                }
                if (!data.hasPlayableSource()) {
                    return@get call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("No playable streams available", "no_playable_streams"),
                    )
                }
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

private suspend fun fetchYoutubeSession(
    userId: String?,
    url: String,
    deliveryMode: StreamDeliveryMode,
    youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
): ExtractionResult<StreamResponse>? =
    if (deliveryMode.isYoutube() && userId != null && youtubeSessionStreamInfo != null) {
        youtubeSessionStreamInfo(userId, url)
    } else {
        null
    }

private fun ExtractionResult<StreamResponse>?.forDeliveryMode(
    deliveryMode: StreamDeliveryMode,
): ExtractionResult<StreamResponse>? = when {
    this !is ExtractionResult.Success -> this
    deliveryMode.isSabr() -> ExtractionResult.Success(data.withSabrManifestUrls().onlySabrStreams())
    else -> ExtractionResult.Success(data.withoutSabrStreams())
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

private fun ExtractionResult<StreamResponse>?.hasPlayableSource(): Boolean =
    this is ExtractionResult.Success && data.hasPlayableSource()

private fun StreamResponse.hasPlayableSource(): Boolean =
    playableStreamCount() > 0 || hlsUrl.isNotBlank() || dashMpdUrl.isNotBlank()

private fun StreamResponse.playableStreamCount(): Int =
    videoStreams.size + videoOnlyStreams.size + audioStreams.size

private fun StreamResponse.withSignedPublicHlsUrl(
    shouldSign: Boolean,
    tokenService: PublicHlsManifestTokenService?,
): StreamResponse {
    if (!shouldSign || tokenService == null || hlsUrl.isBlank()) return this
    if (hlsUrl.startsWith("/streams/hls-manifest?token=")) return this
    return copy(hlsUrl = tokenService.createPath(hlsUrl))
}

private fun ExtractionResult.BadRequest.toErrorResponse(): ErrorResponse =
    if (message == YOUTUBE_SESSION_RECONNECT_ERROR) {
        ErrorResponse(message, "youtube_session_needs_reconnect")
    } else {
        ErrorResponse(message)
    }
