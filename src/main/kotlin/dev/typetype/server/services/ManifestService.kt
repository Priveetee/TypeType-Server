package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoStreamItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ManifestService(private val streamService: StreamService) {

    suspend fun dashManifest(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val info = result.data
        val videos = compatibleVideoStreams(info.videoOnlyStreams)
        val audios = compatibleAudioStreams(info.audioStreams)
        if (videos.isEmpty() && audios.isEmpty())
            return ExtractionResult.Failure("No compatible streams found for DASH manifest")
        return ExtractionResult.Success(buildMpd(videos, audios, info.duration))
    }

    private fun compatibleVideoStreams(streams: List<VideoStreamItem>): List<VideoStreamItem> =
        streams.filter { !it.codec.startsWith("av01") && it.url.isNotBlank() && it.codec.isNotBlank() }
            .sortedWith(compareBy({ codecPriority(it.codec) }, { -(it.bitrate ?: 0) }))

    private fun compatibleAudioStreams(streams: List<AudioStreamItem>): List<AudioStreamItem> =
        streams.filter { it.url.isNotBlank() && it.codec.isNotBlank() }
            .sortedByDescending { it.bitrate ?: 0 }

    private fun codecPriority(codec: String): Int = when {
        codec.startsWith("avc1") -> 0
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 1
        else -> 2
    }

    private fun buildMpd(videos: List<VideoStreamItem>, audios: List<AudioStreamItem>, duration: Long): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"")
        sb.appendLine("     profiles=\"urn:mpeg:dash:profile:full:2011\"")
        sb.appendLine("     type=\"static\"")
        sb.appendLine("     mediaPresentationDuration=\"PT${duration}S\"")
        sb.appendLine("     minBufferTime=\"PT4S\">")
        sb.appendLine("  <Period>")
        videos.groupBy { videoMimeType(it.format) to codecFamily(it.codec) }
            .forEach { (key, streams) -> appendVideoAdaptationSet(sb, key.first, streams) }
        audios.groupBy { audioMimeType(it.format) }
            .forEach { (mime, streams) -> appendAudioAdaptationSet(sb, mime, streams) }
        sb.appendLine("  </Period>")
        sb.append("</MPD>")
        return sb.toString()
    }

    private fun appendVideoAdaptationSet(sb: StringBuilder, mimeType: String, streams: List<VideoStreamItem>) {
        sb.appendLine("    <AdaptationSet mimeType=\"$mimeType\" startWithSAP=\"1\">")
        streams.forEachIndexed { i, s ->
            val height = if (s.height > 0) s.height else resolutionHeight(s.resolution)
            val width = if (s.width > 0) s.width else if (height > 0) height * 16 / 9 else 0
            val bandwidth = (s.bitrate ?: (height * 1000)).coerceAtLeast(1)
            val sizeAttr = if (width > 0 && height > 0) " width=\"$width\" height=\"$height\"" else ""
            sb.appendLine("      <Representation id=\"v-$i\" bandwidth=\"$bandwidth\"$sizeAttr codecs=\"${s.codec}\">")
            sb.appendLine("        <BaseURL>/proxy?url=${encode(s.url)}</BaseURL>")
            if (s.indexStart > 0L && s.indexEnd > 0L) {
                sb.appendLine("        <SegmentBase indexRange=\"${s.indexStart}-${s.indexEnd}\">")
                sb.appendLine("          <Initialization range=\"${s.initStart}-${s.initEnd}\"/>")
                sb.appendLine("        </SegmentBase>")
            }
            sb.appendLine("      </Representation>")
        }
        sb.appendLine("    </AdaptationSet>")
    }

    private fun appendAudioAdaptationSet(sb: StringBuilder, mimeType: String, streams: List<AudioStreamItem>) {
        sb.appendLine("    <AdaptationSet mimeType=\"$mimeType\">")
        streams.forEachIndexed { i, a ->
            val bandwidth = ((a.bitrate ?: 128) * 1000).coerceAtLeast(1)
            sb.appendLine("      <Representation id=\"a-$i\" bandwidth=\"$bandwidth\" codecs=\"${a.codec}\">")
            sb.appendLine("        <BaseURL>/proxy?url=${encode(a.url)}</BaseURL>")
            if (a.indexStart > 0L && a.indexEnd > 0L) {
                sb.appendLine("        <SegmentBase indexRange=\"${a.indexStart}-${a.indexEnd}\">")
                sb.appendLine("          <Initialization range=\"${a.initStart}-${a.initEnd}\"/>")
                sb.appendLine("        </SegmentBase>")
            }
            sb.appendLine("      </Representation>")
        }
        sb.appendLine("    </AdaptationSet>")
    }

    private fun videoMimeType(format: String): String =
        if (format.lowercase().contains("webm")) "video/webm" else "video/mp4"

    private fun audioMimeType(format: String): String =
        if (format.lowercase().contains("webm")) "audio/webm" else "audio/mp4"

    private fun codecFamily(codec: String): String = when {
        codec.startsWith("avc1") -> "avc"
        codec.startsWith("vp9") || codec.startsWith("vp09") -> "vp9"
        else -> "other"
    }

    private fun resolutionHeight(resolution: String): Int =
        Regex("(\\d+)p").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun encode(url: String): String =
        URLEncoder.encode(url, StandardCharsets.UTF_8)

    private fun <T> ExtractionResult<T>.recast(): ExtractionResult<String> = when (this) {
        is ExtractionResult.Success -> ExtractionResult.Success(data.toString())
        is ExtractionResult.BadRequest -> ExtractionResult.BadRequest(message)
        is ExtractionResult.Failure -> ExtractionResult.Failure(message)
    }
}
