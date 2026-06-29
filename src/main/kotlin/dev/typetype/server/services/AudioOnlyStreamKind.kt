package dev.typetype.server.services

enum class AudioOnlyStreamKind(val apiValue: String) {
    Progressive("progressive"),
    Hls("hls"),
}
