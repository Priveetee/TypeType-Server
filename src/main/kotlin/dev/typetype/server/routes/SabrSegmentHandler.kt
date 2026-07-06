package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.markServed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal class SabrSegmentHandler(
    private val sabrSessionStore: SabrSessionStore,
    private val authService: AuthService?,
    private val accessControlService: AccessControlService?,
    private val adminSettingsService: AdminSettingsService?,
) {
    suspend fun handle(call: ApplicationCall, isInit: Boolean, seq: Int) {
        val videoId = call.parameters["videoId"]
        val itag = call.parameters["itag"]?.toIntOrNull()
        if (videoId == null || itag == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid path"))
            return
        }
        val holder = if (call.request.queryParameters["session"] != null) {
            sabrSessionStore.lookupByToken(videoId, call.request.queryParameters["session"].orEmpty(), itag)
        } else {
            val access = call.accessProfileOrRespond(authService, accessControlService, adminSettingsService) ?: return
            sabrSessionStore.lookupByItag(videoId, access.userId ?: videoId, itag)
        } ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR session for this request"))
        val format = if (holder.audioFormat.itag == itag) holder.audioFormat else holder.videoFormat
        if (isInit) {
            val bytes = withTimeoutOrNull(20_000L) {
                sabrSessionStore.fetchInitializationData(holder, format)
            } ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
            call.response.headers.append("Cache-Control", "no-store")
            return call.respondOutputStream(containerMime(format.mimeType.orEmpty())) { write(bytes) }
        }
        if (seq < 1) return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
        val request = SabrSegmentRequest.media(format, seq)
        sabrSessionStore.cachedSegment(holder, request)?.let { cached ->
            holder.markServed(cached)
            call.response.headers.append("Cache-Control", "no-store")
            return call.respondOutputStream(containerMime(cached.mimeType)) { write(cached.bytes) }
        }
        val segment = withTimeoutOrNull(20_000L) { sabrSessionStore.fetchSegment(holder, request) }
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
        holder.markServed(segment)
        call.response.headers.append("Cache-Control", "no-store")
        call.respondOutputStream(containerMime(format.mimeType.orEmpty())) {
            write(segment.data)
        }
    }

}
