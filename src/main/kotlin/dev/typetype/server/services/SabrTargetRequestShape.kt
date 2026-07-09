package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal inline fun <T> withTargetedRequestShape(
    holder: SabrSessionHolder,
    request: SabrSegmentRequest,
    block: () -> T,
): T {
    val companion = holder.companionFormat(request.format)
    val state = holder.session.streamState
    val targetIsAudio = request.format.isAudio
    state.setRequestTrackMode(targetMode(targetIsAudio), true, true)
    state.setSelectVideoFormatBeforeAudio(targetIsAudio)
    state.setLastOnlyRange(request.format, true)
    state.setFullyBuffered(companion, true)
    return try {
        block()
    } finally {
        state.setFullyBuffered(companion, false)
        state.setLastOnlyRange(request.format, false)
        state.setActiveTrackTypes(holder.isVideoActive(), holder.isAudioActive())
        state.setSelectVideoFormatBeforeAudio(holder.playerTimeMs() > SEEK_FORMAT_ORDER_MS)
    }
}

private fun SabrSessionHolder.companionFormat(format: YoutubeSabrFormat): YoutubeSabrFormat =
    if (format.isAudio) videoFormat else audioFormat

private fun targetMode(targetIsAudio: Boolean): Int = if (targetIsAudio) {
    YoutubeSabrStreamState.TRACK_MODE_AUDIO_ONLY
} else {
    YoutubeSabrStreamState.TRACK_MODE_VIDEO_ONLY
}

private const val SEEK_FORMAT_ORDER_MS = 1_000L
