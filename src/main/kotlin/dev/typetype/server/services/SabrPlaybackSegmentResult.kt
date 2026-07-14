package dev.typetype.server.services

internal sealed class SabrPlaybackSegmentResult {
    data class Ready(val mimeType: String, val bytes: ByteArray) : SabrPlaybackSegmentResult()
    data class Retry(val holder: SabrSessionHolder, val status: String) : SabrPlaybackSegmentResult()
    data class Stale(val holder: SabrSessionHolder) : SabrPlaybackSegmentResult()
    data object InvalidSequence : SabrPlaybackSegmentResult()
    data object InvalidGeneration : SabrPlaybackSegmentResult()
}
