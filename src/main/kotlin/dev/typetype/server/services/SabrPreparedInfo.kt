package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal class SabrPreparedInfo(
    val info: YoutubeSabrInfo,
    val initialToken: SabrTokenBundle?,
    val isLive: Boolean = false,
    val isLiveContent: Boolean = false,
    val source: SabrPreparedSource = SabrPreparedSource.PUBLIC,
)

internal fun SabrPreparedInfo.hasAudioAndVideoFormats(): Boolean =
    info.formats.any { it.isAudio } && info.formats.any { it.isVideo }
