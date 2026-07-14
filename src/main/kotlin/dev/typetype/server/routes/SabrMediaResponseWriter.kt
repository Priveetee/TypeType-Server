package dev.typetype.server.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes

internal suspend fun ApplicationCall.respondSabrMediaBytes(mimeType: String, body: ByteArray): Unit {
    val total = body.size.toLong()
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append(HttpHeaders.AcceptRanges, "bytes")
    when (val range = parseAudioOnlyByteRange(request.headers[HttpHeaders.Range], total)) {
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
