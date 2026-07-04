package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal class SabrPreparedInfo(
    val info: YoutubeSabrInfo,
    val initialToken: SabrTokenBundle,
)
