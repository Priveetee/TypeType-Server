package dev.typetype.server

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip

fun Application.configureCompression(): Unit {
    install(Compression) {
        gzip {
            excludeContentType(ContentType.parse("application/vnd.apple.mpegurl"))
            excludeContentType(ContentType.Image.Any)
            excludeContentType(ContentType.Video.Any)
            excludeContentType(ContentType.Audio.Any)
            excludeContentType(ContentType.Application.OctetStream)
        }
    }
}
