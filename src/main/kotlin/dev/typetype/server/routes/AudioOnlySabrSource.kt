package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AudioOnlyMediaToken
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import kotlin.math.max

internal suspend fun ApplicationCall.respondSabrAudioOnlySource(
    sabrSessionStore: SabrSessionStore,
    token: AudioOnlyMediaToken,
    selection: AudioOnlyStreamSelection,
) {
    val videoId = token.videoUrl.youtubeVideoId()
        ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid YouTube URL"))
    val prepared = sabrSessionStore.fetchInfo(videoId, cachedFirst = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
    val audio = SabrFormatSelector.audio(prepared.info, selection.stream.itag, token.selectedAudioTrackId, requireAac = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
    val video = SabrFormatSelector.video(prepared.info, null)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR video for this video"))
    val holder = sabrSessionStore.getOrCreate(
        videoId,
        token.userId ?: videoId,
        prepared.info,
        audio,
        video,
        prepared.initialToken,
        startPump = false,
    )
    holder.setActiveTracks(videoActive = false, audioActive = true)
    val init = sabrSessionStore.fetchInitializationData(holder, audio)
        ?: return respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append("Accept-Ranges", "none")
    respondOutputStream(containerMime(audio.mimeType.orEmpty())) {
        write(init)
        var positionMs = 0L
        var lastSequence = 0
        repeat(maxSegmentCount(audio.approxDurationMs)) {
            val segments = sabrSessionStore.fetchMediaAt(holder, positionMs).orEmpty()
                .filter { it.header.itag == audio.itag && it.header.sequenceNumber > lastSequence }
            if (segments.isEmpty()) return@respondOutputStream
            segments.forEach { write(it.data) }
            flush()
            lastSequence = segments.maxOf { it.header.sequenceNumber }
            positionMs = nextPosition(positionMs, segments)
        }
    }
}

private fun maxSegmentCount(durationMs: Long): Int {
    val durationBased = (durationMs.coerceAtLeast(1L) / 1_000L + 20L).coerceAtLeast(60L)
    return durationBased.coerceAtMost(5_000L).toInt()
}

private fun nextPosition(currentMs: Long, segments: List<SabrMediaSegment>): Long {
    val endMs = segments.maxOf { it.header.startMs + it.header.durationMs }
    return max(endMs, currentMs + 1L)
}
