package dev.typetype.server.services

internal class AndroidDashManifestService {
    fun build(holder: SabrSessionHolder): AndroidDashManifestResult {
        if (holder.expectsLive()) return AndroidDashManifestResult.UnsupportedLive
        val state = holder.session.streamState
        val audio = AndroidDashTimelineReader.read(state, holder.audioFormat)
        val video = AndroidDashTimelineReader.read(state, holder.videoFormat)
        if (audio is AndroidDashTimelineResult.Pending || video is AndroidDashTimelineResult.Pending) {
            return AndroidDashManifestResult.Preparing
        }
        if (audio is AndroidDashTimelineResult.Invalid) return AndroidDashManifestResult.Invalid(audio.reason)
        if (video is AndroidDashTimelineResult.Invalid) return AndroidDashManifestResult.Invalid(video.reason)
        audio as AndroidDashTimelineResult.Ready
        video as AndroidDashTimelineResult.Ready
        val durationMs = maxOf(audio.timeline.endMs, video.timeline.endMs)
        if (durationMs <= 0L || durationMs - audio.timeline.endMs > COVERAGE_TOLERANCE_MS ||
            durationMs - video.timeline.endMs > COVERAGE_TOLERANCE_MS
        ) {
            return AndroidDashManifestResult.Invalid("Audio and video timelines do not cover the same presentation")
        }
        val manifest = AndroidDashManifestBuilder.build(
            sessionId = holder.sessionToken,
            generation = holder.activeGeneration(),
            audio = AndroidDashTrack(holder.audioFormat, audio.timeline),
            video = AndroidDashTrack(holder.videoFormat, video.timeline),
            durationMs = durationMs,
        )
        if (manifest.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            return AndroidDashManifestResult.Invalid("DASH manifest exceeds the size limit")
        }
        return AndroidDashManifestResult.Ready(manifest, durationMs)
    }

    private companion object {
        const val COVERAGE_TOLERANCE_MS = 1_500L
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    }
}

internal sealed interface AndroidDashManifestResult {
    data class Ready(val manifest: String, val durationMs: Long) : AndroidDashManifestResult
    data object Preparing : AndroidDashManifestResult
    data object UnsupportedLive : AndroidDashManifestResult
    data class Invalid(val reason: String) : AndroidDashManifestResult
}
