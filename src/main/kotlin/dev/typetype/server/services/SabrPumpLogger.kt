package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.slf4j.LoggerFactory

internal object SabrPumpLogger {
    private val logger = LoggerFactory.getLogger(SabrSessionPumpLoop::class.java)

    fun start(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?): Unit {
        logger.info(
            "sabr_pump event={}_start videoId={} request={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    fun finish(holder: SabrSessionHolder, event: String, request: SabrSegmentRequest?, pumped: Int): Unit {
        logger.info(
            "sabr_pump event={}_finish videoId={} request={} pumped={} cached={} state={} requestNumber={} edgeMs={} readerHeadMs={} readerTailMs={} cachedBytes={}",
            event,
            holder.key.videoId,
            request?.summary(),
            pumped,
            request?.let { holder.session.getCachedSegment(it) != null },
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.readerHeadMs(),
            holder.readerTailMs(),
            holder.session.cachedBytes,
        )
    }

    fun failure(holder: SabrSessionHolder, error: Exception): Unit {
        logger.warn(
            "sabr_pump event=round_failed videoId={} state={} requestNumber={} edgeMs={} cachedBytes={} errorType={} error={}",
            holder.key.videoId,
            holder.playbackState(),
            holder.session.requestNumber,
            holder.session.streamState.getMinBufferedEndMs(),
            holder.session.cachedBytes,
            error.javaClass.simpleName,
            error.message,
            error,
        )
    }

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
