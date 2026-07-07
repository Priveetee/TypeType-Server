package dev.typetype.server.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.slf4j.LoggerFactory

internal class SabrPlaybackSegmentFetcher(
    private val fetchSegment: suspend (SabrSessionHolder, SabrSegmentRequest) -> SabrMediaSegment?,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
    private val refetchAfterMs: Long = REFETCH_AFTER_MS,
    private val recoveryFailureMs: Long = RECOVERY_FAILURE_MS,
    private val forwardSeekAheadMs: Long = FORWARD_SEEK_AHEAD_MS,
) {
    suspend fun fetch(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        sequence: Int,
        timeoutMs: Long,
    ): SabrMediaSegment? {
        val request = SabrSegmentRequest.media(format, sequence)
        return withTimeoutOrNull(timeoutMs) {
            val waitStart = System.currentTimeMillis()
            var recoveryAtMs = -1L
            while (true) {
                holder.terminalFailure()?.let { return@withTimeoutOrNull null }
                holder.consumeNetworkFailure()?.let { return@withTimeoutOrNull null }
                fetchSegment(holder, request)?.let { return@withTimeoutOrNull it }
                val now = System.currentTimeMillis()
                if (recoveryAtMs < 0L && now - waitStart >= refetchAfterMs) {
                    logRecovery(holder, request)
                    holder.recoverFor(request)
                    recoveryAtMs = now
                }
                if (recoveryAtMs >= 0L && now - recoveryAtMs > recoveryFailureMs) {
                    logger.warn(
                        "sabr_playback_recovery_failed videoId={} itag={} seq={} edgeMs={} readerHeadMs={} readerTailMs={} state={}",
                        holder.key.videoId,
                        request.format.itag,
                        request.sequenceNumber,
                        holder.session.streamState.getMinBufferedEndMs(),
                        holder.readerHeadMs(),
                        holder.readerTailMs(),
                        holder.playbackState(),
                    )
                    holder.failTerminal("SABR playback recovery made no progress")
                    return@withTimeoutOrNull null
                }
                delay(retryDelayMs)
            }
            null
        }
    }

    private fun SabrSessionHolder.recoverFor(request: SabrSegmentRequest) {
        val startMs = session.streamState.getSegmentStartMs(request.format, request.sequenceNumber).coerceAtLeast(0L)
        setReaderPosition(request.format, startMs)
        val edgeMs = session.streamState.getMinBufferedEndMs()
        if (startMs < edgeMs) {
            requestRefetch(request)
        } else if (startMs > edgeMs + forwardSeekAheadMs) {
            requestForwardSeek(request)
        } else {
            requestForwardSeek(request)
        }
    }

    private fun logRecovery(holder: SabrSessionHolder, request: SabrSegmentRequest) {
        val startMs = holder.session.streamState.getSegmentStartMs(request.format, request.sequenceNumber)
        logger.warn(
            "sabr_playback_recovery videoId={} itag={} seq={} startMs={} edgeMs={} readerHeadMs={} readerTailMs={} state={}",
            holder.key.videoId,
            request.format.itag,
            request.sequenceNumber,
            startMs,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.playbackState(),
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrPlaybackSegmentFetcher::class.java)
        const val RETRY_DELAY_MS = 250L
        const val REFETCH_AFTER_MS = 2_000L
        const val RECOVERY_FAILURE_MS = 10_000L
        const val FORWARD_SEEK_AHEAD_MS = 30_000L
    }
}
