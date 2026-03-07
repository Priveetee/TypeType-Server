package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoStreamItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ManifestService(private val streamService: StreamService) {

    suspend fun masterPlaylist(videoUrl: String): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val info = result.data
        val videos = compatibleVideoStreams(info.videoOnlyStreams)
        val audio = bestAudio(info.audioStreams)
        if (videos.isEmpty() || audio == null) {
            return ExtractionResult.Failure("No compatible streams found for HLS manifest")
        }
        return ExtractionResult.Success(buildMaster(videoUrl, videos, audio, info.duration))
    }

    suspend fun mediaPlaylist(videoUrl: String, index: Int, duration: Long): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val videos = compatibleVideoStreams(result.data.videoOnlyStreams)
        val stream = videos.getOrNull(index)
            ?: return ExtractionResult.BadRequest("Stream index $index not found")
        return ExtractionResult.Success(buildMediaPlaylist(stream.url, duration))
    }

    suspend fun audioPlaylist(videoUrl: String, duration: Long): ExtractionResult<String> {
        val result = streamService.getStreamInfo(videoUrl)
        if (result !is ExtractionResult.Success) return result.recast()
        val audio = bestAudio(result.data.audioStreams)
            ?: return ExtractionResult.Failure("No compatible audio stream found")
        return ExtractionResult.Success(buildMediaPlaylist(audio.url, duration))
    }

    private fun compatibleVideoStreams(streams: List<VideoStreamItem>): List<VideoStreamItem> =
        streams.filter { it.format == "MPEG-4" && it.url.isNotBlank() }
            .sortedByDescending { it.bitrate ?: resolutionHeight(it.resolution) * 1000 }

    private fun bestAudio(streams: List<AudioStreamItem>): AudioStreamItem? =
        streams.filter { it.format == "m4a" && it.url.isNotBlank() }
            .maxByOrNull { it.bitrate ?: 0 }

    private fun buildMaster(videoUrl: String, videos: List<VideoStreamItem>, audio: AudioStreamItem, duration: Long): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:3")
        val encodedVideoUrl = encode(videoUrl)
        val audioUri = "/streams/manifest/audio?url=$encodedVideoUrl&duration=$duration"
        sb.appendLine("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"default\",DEFAULT=YES,URI=\"$audioUri\"")
        videos.forEachIndexed { i, v ->
            val bandwidth = (v.bitrate ?: (resolutionHeight(v.resolution) * 1000)).coerceAtLeast(1)
            val height = resolutionHeight(v.resolution)
            val resolution = if (height > 0) "RESOLUTION=${height * 16 / 9}x$height," else ""
            val codec = v.codec.takeIf { it.isNotBlank() }?.let { "CODECS=\"$it,mp4a.40.2\"," } ?: ""
            val mediaUri = "/streams/manifest/media?url=$encodedVideoUrl&index=$i&duration=$duration"
            sb.appendLine("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,${resolution}${codec}AUDIO=\"audio\"")
            sb.appendLine(mediaUri)
        }
        return sb.toString().trimEnd()
    }

    private fun buildMediaPlaylist(streamUrl: String, duration: Long): String {
        val safeDuration = duration.coerceAtLeast(1L)
        val proxied = "/proxy?url=${encode(streamUrl)}"
        return buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:$safeDuration")
            appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
            appendLine("#EXTINF:$safeDuration.0,")
            appendLine(proxied)
            append("#EXT-X-ENDLIST")
        }
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
