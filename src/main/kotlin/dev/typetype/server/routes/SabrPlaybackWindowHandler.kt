package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrPlaybackDiagnostics
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.pendingSegmentDemandSummary
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest

internal class SabrPlaybackWindowHandler(private val sabrSessionStore: SabrSessionStore) {
    private val windowBuilder = SabrPlaybackWindowBuilder(sabrSessionStore)

    suspend fun post(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid window request"))
        val holder = validatedHolder(call, sessionId, request) ?: return

        holder.setPlayerTimeMs(request.playerTimeMs)
        holder.applyClientState(request.bufferedRanges)
        sabrSessionStore.startPump(holder)

        val window = buildWithTargetedPrefetch(holder, request)
        if (window.isReady) {
            return call.respond(HttpStatusCode.OK, window.response)
        }
        call.respond(HttpStatusCode.Accepted, holder.preparingResponse(request, window.blockedBy ?: "window pending"))
    }

    suspend fun position(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackPositionRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid position request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        holder.setPlayerTimeMs(request.playerTimeMs)
        holder.applyClientState(request.bufferedRanges)
        call.respond(
            SabrPlaybackPositionResponse(
                sessionId = holder.sessionToken,
                generation = holder.activeGeneration(),
                playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
                readerHeadMs = holder.readerHeadMs(),
                readerTailMs = holder.readerTailMs(),
                bufferedEdgeMs = holder.session.streamState.getMinBufferedEndMs(),
            )
        )
    }

    suspend fun prefetch(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid prefetch request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        holder.setPlayerTimeMs(request.playerTimeMs)
        holder.applyClientState(request.bufferedRanges)
        sabrSessionStore.startPump(holder)
        val window = buildWithTargetedPrefetch(holder, request)
        val status = if (window.isReady) HttpStatusCode.OK else HttpStatusCode.Accepted
        call.respond(status, holder.prefetchResponse(request, window))
    }

    suspend fun segments(call: ApplicationCall, sessionId: String) {
        val request = runCatching { call.receive<SabrPlaybackWindowRequest>() }.getOrNull()
            ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid segments request"))
        val holder = validatedHolder(call, sessionId, request) ?: return
        holder.applyClientState(request.bufferedRanges)
        val window = windowBuilder.build(holder, request)
        if (window.isReady) return call.respond(HttpStatusCode.OK, window.response)
        call.respond(HttpStatusCode.Accepted, holder.preparingResponse(request, window.blockedBy ?: "window pending"))
    }

    private suspend fun buildWithTargetedPrefetch(
        holder: SabrSessionHolder,
        request: SabrPlaybackWindowRequest,
    ): SabrPlaybackWindowBuildResult {
        val window = windowBuilder.build(holder, request)
        val blockedRequest = window.blockedRequest ?: return window
        sabrSessionStore.requestSegmentDemand(holder, blockedRequest)
        return window
    }

    private fun SabrSessionHolder.preparingResponse(request: SabrPlaybackWindowRequest, blockedBy: String): SabrPlaybackWindowPreparingResponse =
        SabrPlaybackWindowPreparingResponse(
            sessionId = sessionToken,
            generation = activeGeneration(),
            ready = false,
            retryAfterMs = RETRY_AFTER_MS,
            status = playbackState().name.lowercase(),
            blockedBy = SabrPlaybackDiagnostics.blocker(this) ?: blockedBy,
            playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
            readerHeadMs = readerHeadMs(),
            readerTailMs = readerTailMs(),
            bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
            pendingRefetch = pendingRefetchRequest()?.summary(),
            pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
            pendingSegmentDemand = pendingSegmentDemandSummary(),
            terminalError = terminalFailure(),
            recoveryAction = recoveryAction(),
            retryVideoItags = retryVideoItags(),
        )

    private fun SabrSessionHolder.prefetchResponse(
        request: SabrPlaybackWindowRequest,
        window: SabrPlaybackWindowBuildResult,
    ): SabrPlaybackPrefetchResponse = SabrPlaybackPrefetchResponse(
        sessionId = sessionToken,
        generation = activeGeneration(),
        ready = window.isReady,
        retryAfterMs = if (window.isReady) null else RETRY_AFTER_MS,
        status = playbackState().name.lowercase(),
        segmentsUrl = "${SabrPlaybackPaths.mediaBasePath(sessionToken)}/segments",
        stateUrl = "${SabrPlaybackPaths.mediaBasePath(sessionToken)}/state",
        blockedBy = if (window.isReady) null else SabrPlaybackDiagnostics.blocker(this) ?: window.blockedBy ?: "window pending",
        playerTimeMs = request.playerTimeMs.coerceAtLeast(0L),
        readerHeadMs = readerHeadMs(),
        readerTailMs = readerTailMs(),
        bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
        pendingRefetch = pendingRefetchRequest()?.summary(),
        pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
        pendingSegmentDemand = pendingSegmentDemandSummary(),
        terminalError = terminalFailure(),
        recoveryAction = recoveryAction(),
        retryVideoItags = retryVideoItags(),
    )

    private suspend fun validatedHolder(
        call: ApplicationCall,
        sessionId: String,
        request: SabrPlaybackWindowRequest,
    ): SabrSessionHolder? {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session")).let { null }
        if (!holder.matches(request)) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Window formats do not match session")).let { null }
        }
        if (request.generation != holder.activeGeneration()) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation")).let { null }
        }
        return holder
    }

    private suspend fun validatedHolder(
        call: ApplicationCall,
        sessionId: String,
        request: SabrPlaybackPositionRequest,
    ): SabrSessionHolder? {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session")).let { null }
        if (!holder.matches(request)) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Position formats do not match session")).let { null }
        }
        if (request.generation != holder.activeGeneration()) {
            return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation")).let { null }
        }
        return holder
    }

    private fun SabrSessionHolder.matches(request: SabrPlaybackWindowRequest): Boolean =
        request.videoItag == videoFormat.itag && request.audioItag == audioFormat.itag && request.audioTrackId == audioFormat.audioTrackId

    private fun SabrSessionHolder.matches(request: SabrPlaybackPositionRequest): Boolean =
        request.videoItag == videoFormat.itag && request.audioItag == audioFormat.itag && request.audioTrackId == audioFormat.audioTrackId

    private fun SabrSessionHolder.recoveryAction(): String? = terminalFailure()
        ?.takeIf { it.contains("protected no-media") }
        ?.let { "retry_fresh_session_lower_video_itag" }

    private fun SabrSessionHolder.retryVideoItags(): List<Int> = if (recoveryAction() == null) emptyList() else {
        info.formats.asSequence()
            .filter { it.isVideo && it.itag != videoFormat.itag }
            .sortedByDescending { it.bitrate }
            .map { it.itag }
            .distinct()
            .take(MAX_RETRY_VIDEO_ITAGS)
            .toList()
    }

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"

    private companion object {
        const val RETRY_AFTER_MS = 500L
        const val MAX_RETRY_VIDEO_ITAGS = 5
    }
}
