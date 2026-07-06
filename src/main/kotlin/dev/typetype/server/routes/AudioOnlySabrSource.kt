package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AudioOnlyMediaToken
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.SabrSessionStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream

internal suspend fun ApplicationCall.respondSabrAudioOnlySource(
    sabrSessionStore: SabrSessionStore,
    token: AudioOnlyMediaToken,
    selection: AudioOnlyStreamSelection,
) {
    val videoId = token.videoUrl.youtubeVideoId()
        ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid YouTube URL"))
    AudioOnlySabrBodyCache.get(videoId, selection.stream.itag, token.selectedAudioTrackId)?.let { body ->
        return respondSabrAudioOnlyBytes(selection.stream.mimeType.orEmpty(), body, request.headers[HttpHeaders.Range])
    }
    val prepared = sabrSessionStore.fetchInfo(videoId, cachedFirst = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
    val audio = SabrFormatSelector.audio(prepared.info, selection.stream.itag, token.selectedAudioTrackId, requireAac = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
    val video = SabrFormatSelector.lightestVideo(prepared.info)
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
    holder.setActiveTracks(videoActive = true, audioActive = true)
    val init = sabrSessionStore.fetchInitializationData(holder, audio)
        ?: return respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
    val body = materializeSabrAudioOnlyBody(sabrSessionStore, holder, init)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No complete SABR audio for this video"))
    AudioOnlySabrBodyCache.put(videoId, audio.itag, audio.audioTrackId, body)
    respondSabrAudioOnlyBytes(audio.mimeType.orEmpty(), body, request.headers[HttpHeaders.Range])
}

internal suspend fun ApplicationCall.respondSabrAudioOnlyHead(
    sabrSessionStore: SabrSessionStore,
    token: AudioOnlyMediaToken,
    selection: AudioOnlyStreamSelection,
) {
    val videoId = token.videoUrl.youtubeVideoId()
        ?: return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid YouTube URL"))
    AudioOnlySabrBodyCache.get(videoId, selection.stream.itag, token.selectedAudioTrackId)?.let { body ->
        return respondSabrAudioOnlyHeadBytes(selection.stream.mimeType.orEmpty(), body)
    }
    val prepared = sabrSessionStore.fetchInfo(videoId, cachedFirst = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("SABR probe failed"))
    val audio = SabrFormatSelector.audio(prepared.info, selection.stream.itag, token.selectedAudioTrackId, requireAac = true)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No SABR audio for this video"))
    val video = SabrFormatSelector.lightestVideo(prepared.info)
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
    holder.setActiveTracks(videoActive = true, audioActive = true)
    val init = sabrSessionStore.fetchInitializationData(holder, audio)
        ?: return respond(HttpStatusCode.NotFound, ErrorResponse("Segment not available"))
    val body = materializeSabrAudioOnlyBody(sabrSessionStore, holder, init)
        ?: return respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("No complete SABR audio for this video"))
    AudioOnlySabrBodyCache.put(videoId, audio.itag, audio.audioTrackId, body)
    respondSabrAudioOnlyHeadBytes(audio.mimeType.orEmpty(), body)
}

private suspend fun ApplicationCall.respondSabrAudioOnlyHeadBytes(
    mimeType: String,
    body: ByteArray,
): Unit {
    val total = body.size.toLong()
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    when (val range = parseAudioOnlyByteRange(request.headers[HttpHeaders.Range], total)) {
        is AudioOnlyByteRange.Satisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/${range.total}")
            respondOutputStream(
                containerMime(mimeType),
                HttpStatusCode.PartialContent,
                range.last - range.first + 1L,
            ) {}
        }
        is AudioOnlyByteRange.Unsatisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes */${range.total}")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        null -> {
            respondOutputStream(containerMime(mimeType), HttpStatusCode.OK, total) {}
        }
    }
}

private suspend fun ApplicationCall.respondSabrAudioOnlyBytes(
    mimeType: String,
    body: ByteArray,
    rangeHeader: String?,
): Unit {
    val total = body.size.toLong()
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    when (val range = parseAudioOnlyByteRange(rangeHeader, total)) {
        is AudioOnlyByteRange.Satisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes ${range.first}-${range.last}/${range.total}")
            respondBytes(
                body.copyOfRange(range.first.toInt(), (range.last + 1L).toInt()),
                containerMime(mimeType),
                HttpStatusCode.PartialContent,
            )
        }
        is AudioOnlyByteRange.Unsatisfiable -> {
            response.headers.append(HttpHeaders.ContentRange, "bytes */${range.total}")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        null -> respondBytes(body, containerMime(mimeType), HttpStatusCode.OK)
    }
}
