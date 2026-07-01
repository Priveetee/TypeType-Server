package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.util.Locale

internal object SabrHlsManifestBuilder {
    fun buildAudioOnly(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val n = endSegmentAudio.coerceAtLeast(1L).toInt()
        val durations = (1..n).map { seq -> SabrManifestTiming.segmentDurationMs(audio, seq, n, streamState) }
        val sessionQuery = "?session=$sessionToken"
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-TARGETDURATION:${targetDuration(durations)}")
        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:1")
        sb.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        sb.appendLine("#EXT-X-MAP:URI=\"/sabr/$videoId/${audio.itag}/init$sessionQuery\"")
        for (seq in 1..n) {
            sb.appendLine("#EXTINF:${String.format(Locale.US, "%.3f", durations[seq - 1] / 1000.0)},")
            sb.appendLine("/sabr/$videoId/${audio.itag}/segment/$seq$sessionQuery")
        }
        sb.appendLine("#EXT-X-ENDLIST")
        return sb.toString()
    }

    private fun targetDuration(durations: List<Long>): Long =
        durations.maxOrNull()?.let { ((it + 999L) / 1000L).coerceAtLeast(1L) } ?: 1L
}
