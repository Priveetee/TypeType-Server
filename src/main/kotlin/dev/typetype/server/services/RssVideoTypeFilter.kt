package dev.typetype.server.services

import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.models.VideoItem

internal object RssVideoTypeFilter {
    fun includes(feed: RssFeedItem, video: VideoItem, now: Long): Boolean = when {
        video.isLive -> feed.includeLive
        isUpcoming(video, now) -> feed.includeUpcoming
        video.isShortFormContent -> feed.includeShorts
        else -> feed.includeVideos
    }

    private fun isUpcoming(video: VideoItem, now: Long): Boolean =
        !video.isPostLive && video.duration < 0 && RssVideoMetadata.publishedAtMillis(video) > now
}
