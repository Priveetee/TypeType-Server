package dev.typetype.server.models

data class ProxyResponse(
    val contentType: String,
    val body: ByteArray,
)
