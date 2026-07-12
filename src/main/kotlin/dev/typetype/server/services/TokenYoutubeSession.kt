package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo

internal data class TokenYoutubeSession(
    val info: YoutubeSabrInfo,
    val title: String,
    val author: String,
    val channelId: String,
    val description: String,
    val durationMs: Long,
    val viewCount: Long,
    val thumbnailUrl: String,
    val tags: List<String>,
    val isLive: Boolean,
    val isLiveContent: Boolean,
)
