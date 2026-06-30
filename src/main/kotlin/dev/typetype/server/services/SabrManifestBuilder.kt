package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState
import java.util.Locale

internal object SabrManifestBuilder {

    fun build(
        videoId: String,
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        endSegmentAudio: Long,
        endSegmentVideo: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val durationSec = videoDurationSec(audio, video, streamState)
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${durationSec}S\"")
        sb.appendLine("     minBufferTime=\"PT2S\">")
        sb.appendLine("  <Period>")
        appendVideoAdaptation(sb, videoId, video, endSegmentVideo, streamState, sessionToken)
        appendAudioAdaptation(sb, videoId, audio, endSegmentAudio, streamState, sessionToken)
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    fun buildAudioOnly(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val durationSec = audioDurationSec(audio, streamState)
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${durationSec}S\"")
        sb.appendLine("     minBufferTime=\"PT2S\">")
        sb.appendLine("  <Period>")
        appendAudioAdaptation(sb, videoId, audio, endSegmentAudio, streamState, sessionToken)
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    fun buildAudioOnlyHls(
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegmentAudio: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ): String {
        val n = endSegmentAudio.coerceAtLeast(1L).toInt()
        val durations = (1..n).map { seq -> segmentDurationMs(audio, seq, n, streamState) }
        val targetDuration = durations.maxOrNull()?.let { ((it + 999L) / 1000L).coerceAtLeast(1L) } ?: 1L
        val sessionQuery = "?session=$sessionToken"
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-TARGETDURATION:$targetDuration")
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

    private fun appendVideoAdaptation(
        sb: StringBuilder,
        videoId: String,
        video: YoutubeSabrFormat,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ) {
        val (container, codecs) = splitMime(video.mimeType.orEmpty())
        val bandwidth = video.bitrate.coerceAtLeast(1)
        val w = video.width.takeIf { it > 0 }
        val h = video.height.takeIf { it > 0 }
        val sizeAttr = if (w != null && h != null) " width=\"$w\" height=\"$h\"" else ""
        sb.appendLine("    <AdaptationSet mimeType=\"$container\" startWithSAP=\"1\">")
        sb.appendLine("      <Representation id=\"v\" bandwidth=\"$bandwidth\"$sizeAttr codecs=\"$codecs\">")
        appendSegmentList(sb, videoId, video, endSegment, streamState, sessionToken)
        sb.appendLine("      </Representation>")
        sb.appendLine("    </AdaptationSet>")
    }

    private fun appendAudioAdaptation(
        sb: StringBuilder,
        videoId: String,
        audio: YoutubeSabrFormat,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ) {
        val (container, codecs) = splitMime(audio.mimeType.orEmpty())
        val bandwidth = audio.bitrate.coerceAtLeast(128_000)
        sb.appendLine("    <AdaptationSet mimeType=\"$container\">")
        sb.appendLine("      <Representation id=\"a\" bandwidth=\"$bandwidth\" codecs=\"$codecs\">")
        appendSegmentList(sb, videoId, audio, endSegment, streamState, sessionToken)
        sb.appendLine("      </Representation>")
        sb.appendLine("    </AdaptationSet>")
    }

    private fun appendSegmentList(
        sb: StringBuilder,
        videoId: String,
        format: YoutubeSabrFormat,
        endSegment: Long,
        streamState: YoutubeSabrStreamState,
        sessionToken: String,
    ) {
        val n = endSegment.coerceAtLeast(1)
        val totalMs = format.approxDurationMs.coerceAtLeast(1L)
        val avgMs = (totalMs / n).coerceAtLeast(1L)
        val sessionQuery = "?session=$sessionToken"
        sb.appendLine("        <SegmentList timescale=\"1000\">")
        sb.appendLine("          <Initialization sourceURL=\"/sabr/$videoId/${format.itag}/init$sessionQuery\"/>")
        sb.appendLine("          <SegmentTimeline>")
        for (seq in 1..n) {
            val s = streamState.getSegmentStartMs(format, seq.toInt())
            val e = streamState.getSegmentEndMs(format, seq.toInt())
            val start = s.coerceAtLeast(0L)
            val d = (e - start).takeIf { it > 0L } ?: avgMs
            sb.appendLine("            <S t=\"$start\" d=\"$d\"/>")
        }
        sb.appendLine("          </SegmentTimeline>")
        for (seq in 1..n) {
            sb.appendLine("          <SegmentURL media=\"/sabr/$videoId/${format.itag}/segment/$seq$sessionQuery\"/>")
        }
        sb.appendLine("        </SegmentList>")
    }

    private fun splitMime(mime: String): Pair<String, String> {
        val parts = mime.split(";", limit = 2)
        val container = parts[0].trim()
        val codecs = if (parts.size > 1) {
            parts[1].trim().removePrefix("codecs=\"").removeSuffix("\"")
        } else {
            ""
        }
        return container to codecs
    }

    private fun videoDurationSec(
        audio: YoutubeSabrFormat,
        video: YoutubeSabrFormat,
        streamState: YoutubeSabrStreamState,
    ): Long {
        val endSegment = streamState.getEndSegment(video).toInt()
        val indexMs = if (endSegment > 0) streamState.getSegmentEndMs(video, endSegment) else 0L
        val ms = maxOf(indexMs, audio.approxDurationMs, video.approxDurationMs, 0L)
        return (ms / 1000L).coerceAtLeast(1L)
    }

    private fun audioDurationSec(
        audio: YoutubeSabrFormat,
        streamState: YoutubeSabrStreamState,
    ): Long {
        val endSegment = streamState.getEndSegment(audio).toInt()
        val indexMs = if (endSegment > 0) streamState.getSegmentEndMs(audio, endSegment) else 0L
        val ms = maxOf(indexMs, audio.approxDurationMs, 0L)
        return (ms / 1000L).coerceAtLeast(1L)
    }

    private fun segmentDurationMs(
        format: YoutubeSabrFormat,
        seq: Int,
        endSegment: Int,
        streamState: YoutubeSabrStreamState,
    ): Long {
        val start = streamState.getSegmentStartMs(format, seq).coerceAtLeast(0L)
        val end = streamState.getSegmentEndMs(format, seq)
        if (end > start) return end - start
        val totalMs = format.approxDurationMs.coerceAtLeast(1L)
        return (totalMs / endSegment.coerceAtLeast(1)).coerceAtLeast(1L)
    }
}
