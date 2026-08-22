package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

internal fun VideoItem.isUpcomingAt(now: Long): Boolean =
    !isPostLive && RssVideoMetadata.publishedAtMillis(this) > now

internal fun VideoItem.isLiveOrUpcomingAt(now: Long): Boolean = isLive || isUpcomingAt(now)

internal fun VideoItem.isLiveContentOrUpcomingAt(now: Long): Boolean =
    isLive || isPostLive || isLiveContent || isUpcomingAt(now)
