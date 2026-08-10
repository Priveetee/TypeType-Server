package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
internal data class SubscriptionFeedSnapshot(
    val generation: Long,
    val generatedAt: Long,
    val stale: Boolean,
    val videos: List<VideoItem>,
    val livePromotedAt: Map<String, Long> = emptyMap(),
)

@Serializable
private data class SubscriptionFeedCursor(
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val hideLiveStreams: Boolean = false,
)

internal object SubscriptionFeedCursorCodec {
    fun encode(generation: Long, offset: Int, limit: Int, hideLiveStreams: Boolean): String {
        val payload = CacheJson.encodeToString(
            SubscriptionFeedCursor.serializer(),
            SubscriptionFeedCursor(generation, offset, limit, hideLiveStreams),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }

    fun decode(value: String): SubscriptionFeedCursorState? = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(value))
        val cursor = CacheJson.decodeFromString(SubscriptionFeedCursor.serializer(), payload)
        cursor.takeIf { it.generation > 0L && it.offset >= 0 && it.limit in 1..100 }
            ?.let { SubscriptionFeedCursorState(it.generation, it.offset, it.limit, it.hideLiveStreams) }
    }.getOrNull()
}

internal data class SubscriptionFeedCursorState(
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val hideLiveStreams: Boolean,
)

internal fun SubscriptionFeedSnapshot.page(
    offset: Int,
    limit: Int,
    refreshing: Boolean,
    hideLiveStreams: Boolean = false,
): SubscriptionFeedResponse {
    val visibleVideos = if (hideLiveStreams) {
        videos.filterNot { it.isLiveOrUpcomingAt(generatedAt) }
    } else {
        videos
    }
    val from = offset.coerceAtMost(visibleVideos.size)
    val to = minOf(from + limit, visibleVideos.size)
    val nextpage = if (to < visibleVideos.size) {
        SubscriptionFeedCursorCodec.encode(generation, to, limit, hideLiveStreams)
    } else {
        null
    }
    return SubscriptionFeedResponse(
        videos = visibleVideos.subList(from, to),
        nextpage = nextpage,
        generation = generation,
        generatedAt = generatedAt,
        refreshing = refreshing,
    )
}

internal sealed interface SubscriptionFeedPageResult {
    data class Ready(val response: SubscriptionFeedResponse) : SubscriptionFeedPageResult
    data class Preparing(val retryAfterMs: Long) : SubscriptionFeedPageResult
    data object InvalidCursor : SubscriptionFeedPageResult
    data object StaleGeneration : SubscriptionFeedPageResult
}
