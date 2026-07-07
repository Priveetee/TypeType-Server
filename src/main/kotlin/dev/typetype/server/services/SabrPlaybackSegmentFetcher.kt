package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal class SabrPlaybackSegmentFetcher(
    private val fetchSegment: suspend (SabrSessionHolder, SabrSegmentRequest) -> SabrMediaSegment?,
) {
    suspend fun fetch(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        sequence: Int,
        timeoutMs: Long,
    ): SabrMediaSegment? {
        val request = SabrSegmentRequest.media(format, sequence)
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                fetchSegment(holder, request)?.let { return@withTimeoutOrNull it }
                holder.repositionFor(request)
                delay(RETRY_DELAY_MS)
            }
            null
        }
    }

    private fun SabrSessionHolder.repositionFor(request: SabrSegmentRequest) {
        val startMs = session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        setReaderPosition(request.format, startMs)
        val edgeMs = session.streamState.getMinBufferedEndMs()
        if (startMs < edgeMs) {
            requestRefetch(request)
        } else {
            requestForwardSeek(request)
        }
    }

    private companion object {
        const val RETRY_DELAY_MS = 250L
    }
}
