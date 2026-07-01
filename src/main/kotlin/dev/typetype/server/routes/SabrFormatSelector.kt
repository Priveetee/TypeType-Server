package dev.typetype.server.routes

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal object SabrFormatSelector {
    fun video(info: YoutubeSabrInfo, itag: Int?): YoutubeSabrFormat? {
        if (itag != null) return info.findFormatByItag(itag)?.takeIf { it.isVideo }
        return info.formats.filter { it.isVideo && it.mimeType?.contains("mp4") == true && it.mimeType?.contains("avc1") == true }
            .maxByOrNull { it.height }
            ?: info.findBestVideoFormat()
    }

    fun audio(info: YoutubeSabrInfo, itag: Int?, trackId: String?, requireAac: Boolean): YoutubeSabrFormat? {
        if (itag != null) return info.formats.firstOrNull { it.matchesAudio(itag, trackId, requireAac) }
        return info.formats.filter { it.isAac() }.maxByOrNull { it.bitrate }
            ?: if (requireAac) null else info.findBestAudioFormat()
    }

    private fun YoutubeSabrFormat.matchesAudio(itag: Int?, trackId: String?, requireAac: Boolean): Boolean =
        itag != null && isAudio && getItag() == itag && (!requireAac || isAac()) &&
            (trackId.isNullOrBlank() || audioTrackId == trackId)

    private fun YoutubeSabrFormat.isAac(): Boolean =
        isAudio && mimeType?.contains("mp4") == true && mimeType?.contains("mp4a") == true
}
