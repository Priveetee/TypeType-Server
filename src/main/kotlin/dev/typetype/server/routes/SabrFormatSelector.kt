package dev.typetype.server.routes

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal object SabrFormatSelector {
    fun video(info: YoutubeSabrInfo): YoutubeSabrFormat? =
        info.formats.filter { it.isVideo && it.mimeType?.contains("mp4") == true && it.mimeType?.contains("avc1") == true }
            .maxByOrNull { it.height }

    fun audio(info: YoutubeSabrInfo): YoutubeSabrFormat? =
        info.formats.filter { it.isAudio && it.mimeType?.contains("mp4") == true && it.mimeType?.contains("mp4a") == true }
            .maxByOrNull { it.bitrate }
}
