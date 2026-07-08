package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import java.util.concurrent.ConcurrentHashMap

internal object SabrPlaybackDiagnostics {
    private val blockers = ConcurrentHashMap<String, String>()

    fun record(holder: SabrSessionHolder, request: SabrSegmentRequest, message: String?): Unit {
        val blocker = "${request.trackName()}:${request.format.itag}:${request.sequenceNumber} ${message.blockerSummary()}"
        blockers[holder.sessionToken] = blocker
        if (message.isProtectedNoMedia()) holder.failTerminal("$blocker after PO-token refresh attempts")
    }

    fun clear(holder: SabrSessionHolder, segment: SabrMediaSegment): Unit {
        if (!segment.header.isInitSegment) blockers.remove(holder.sessionToken)
    }

    fun blocker(holder: SabrSessionHolder): String? = blockers[holder.sessionToken]

    private fun SabrSegmentRequest.trackName(): String = if (format.isAudio) "audio" else "video"

    private fun String?.blockerSummary(): String = when {
        this == null -> "request failed"
        contains("status=3") -> "status=3 protected no-media"
        contains("reload", ignoreCase = true) -> "reload requested"
        contains("policy", ignoreCase = true) -> "policy-only response"
        else -> this
    }

    private fun String?.isProtectedNoMedia(): Boolean =
        this != null && contains("SABR protected no-media response") && contains("status=3")
}
