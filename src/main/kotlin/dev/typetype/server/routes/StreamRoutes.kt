package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.filterAllowed
import dev.typetype.server.services.withSabrManifestUrls
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val AUTHENTICATED_STREAMS_CACHE_CONTROL = "no-store"

fun Route.streamRoutes(
    streamService: StreamService,
    authService: AuthService? = null,
    accessControlService: AccessControlService? = null,
    adminSettingsService: AdminSettingsService? = null,
    publicHlsManifestTokenService: PublicHlsManifestTokenService? = null,
    nicoNicoStreamService: StreamService = streamService,
    bilibiliStreamService: StreamService = streamService,
    sabrBootstrapStreamService: StreamService = streamService,
    sabrStreamContractFilter: (suspend (String, StreamResponse) -> StreamResponse)? = null,
) {
    val dependencies = StreamRouteDependencies(
        authService = authService,
        accessControlService = accessControlService,
        adminSettingsService = adminSettingsService,
        publicHlsManifestTokenService = publicHlsManifestTokenService,
        sabrStreamContractFilter = sabrStreamContractFilter,
    )
    streamRoute("/streams/youtube/sabr", StreamDeliveryMode.YoutubeSabr, streamService, dependencies)
    streamRoute(
        "/streams/youtube/sabr/bootstrap",
        StreamDeliveryMode.YoutubeSabr,
        sabrBootstrapStreamService,
        dependencies,
    )
    streamRoute("/streams/niconico", StreamDeliveryMode.NicoNico, nicoNicoStreamService, dependencies)
    streamRoute("/streams/bilibili", StreamDeliveryMode.BiliBili, bilibiliStreamService, dependencies)
}

private fun Route.streamRoute(
    path: String,
    deliveryMode: StreamDeliveryMode,
    streamService: StreamService,
    dependencies: StreamRouteDependencies,
) {
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
        val accessProfile = access.profile
        when (val result = streamService.getStreamInfo(url)) {
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
                        deliveryMode.isSabr() && selected.isLive || access.userId != null && !access.allowGuest,
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
                    if (accessProfile.enabled) AUTHENTICATED_STREAMS_CACHE_CONTROL else STREAMS_CACHE_CONTROL,
                )
                call.respond(data)
            }
            is ExtractionResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            is ExtractionResult.Failure ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
        }
    }
}

private fun StreamResponse.hasPlayableSource(): Boolean =
    videoStreams.isNotEmpty() ||
        videoOnlyStreams.isNotEmpty() ||
        audioStreams.isNotEmpty() ||
        hlsUrl.isNotBlank() ||
        dashMpdUrl.isNotBlank()

private fun StreamResponse.withSignedPublicHlsUrl(
    shouldSign: Boolean,
    tokenService: PublicHlsManifestTokenService?,
): StreamResponse {
    if (!shouldSign || tokenService == null || hlsUrl.isBlank()) return this
    if (hlsUrl.startsWith("/streams/hls-manifest?token=")) return this
    return copy(hlsUrl = tokenService.createPath(hlsUrl))
}
