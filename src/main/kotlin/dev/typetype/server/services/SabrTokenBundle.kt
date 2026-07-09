package dev.typetype.server.services

internal class SabrTokenBundle(
    val videoId: String,
    val visitorBoundPoToken: String,
    val visitorBoundPoTokenBytes: ByteArray,
    val visitorData: String,
    val videoBoundPoToken: String,
    val videoBoundPoTokenBytes: ByteArray,
) {
    val visitorPoToken: String = visitorBoundPoToken
    val visitorPoTokenBytes: ByteArray = visitorBoundPoTokenBytes
    val streamingPoToken: String = visitorBoundPoToken
    val streamingPoTokenBytes: ByteArray = visitorBoundPoTokenBytes
}
