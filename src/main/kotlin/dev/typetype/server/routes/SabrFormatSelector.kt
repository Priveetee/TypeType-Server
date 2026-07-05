package dev.typetype.server.routes

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal object SabrFormatSelector {
    fun video(info: YoutubeSabrInfo, itag: Int?): YoutubeSabrFormat? {
        if (itag != null) return info.findFormatByItag(itag)?.takeIf { it.isSupportedVideo() }
        return info.formats.filter { it.isSupportedVideo() }
            .maxByOrNull { it.height }
    }

    fun lightestVideo(info: YoutubeSabrInfo): YoutubeSabrFormat? =
        info.formats.filter { it.isSupportedVideo() }
            .minWithOrNull(compareBy<YoutubeSabrFormat> { it.height }.thenBy { it.bitrate })

    fun audio(info: YoutubeSabrInfo, itag: Int?, trackId: String?, requireAac: Boolean): YoutubeSabrFormat? {
        if (itag != null) return info.formats.filter { it.matchesAudio(itag, trackId, requireAac) }
            .maxWithOrNull(audioComparator)
        return info.formats.filter { it.isAac() && (trackId.isNullOrBlank() || it.audioTrackId == trackId) }
            .maxWithOrNull(audioComparator)
            ?: if (requireAac) null else info.findBestAudioFormat()
    }

    private fun YoutubeSabrFormat.matchesAudio(itag: Int?, trackId: String?, requireAac: Boolean): Boolean =
        itag != null && isAudio && getItag() == itag && (!requireAac || isAac()) &&
            (trackId.isNullOrBlank() || audioTrackId == trackId)

    private fun YoutubeSabrFormat.isAac(): Boolean =
        isAudio && mimeType?.contains("mp4") == true && mimeType?.contains("mp4a") == true

    private fun YoutubeSabrFormat.isSupportedVideo(): Boolean =
        isVideo && mimeType?.contains("mp4") == true && mimeType?.contains("avc1") == true

    private val audioComparator = compareBy<YoutubeSabrFormat> { it.isOriginalAudio }
        .thenBy { it.isPlainAudioVariant() }
        .thenBy { !it.isDrc }
        .thenBy { it.bitrate }

    private fun YoutubeSabrFormat.isPlainAudioVariant(): Boolean = xtags.isNullOrBlank()
}
