package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

object HomeRecommendationMixer {
    private const val MAX_PER_CHANNEL_PER_PAGE = 2
    private const val SUBSCRIPTION_RATIO = 0.6

    fun mix(pool: HomeRecommendationPool, cursor: HomeRecommendationCursor, limit: Int): HomeRecommendationPage {
        val selected = mutableListOf<VideoItem>()
        val channelCount = mutableMapOf<String, Int>()
        var lastChannel = ""
        var subIndex = cursor.subscriptionIndex
        var discoveryIndex = cursor.discoveryIndex
        val subscriptionTarget = (limit * SUBSCRIPTION_RATIO).toInt().coerceIn(1, limit)
        var subscriptionPicked = 0
        while (selected.size < limit) {
            val takeSubscription = subscriptionPicked < subscriptionTarget
            val fromSubscription = if (takeSubscription) {
                val pick = pickNext(pool.subscriptions, subIndex, channelCount, lastChannel)
                subIndex = pick.nextIndex
                pick.video
            } else {
                null
            }
            val fromDiscovery = if (fromSubscription == null) {
                val pick = pickNext(pool.discovery, discoveryIndex, channelCount, lastChannel)
                discoveryIndex = pick.nextIndex
                pick.video
            } else {
                null
            }
            val chosen = fromSubscription ?: fromDiscovery ?: run {
                val fallbackSub = pickNext(pool.subscriptions, subIndex, channelCount, lastChannel)
                subIndex = fallbackSub.nextIndex
                val fallbackDisc = if (fallbackSub.video == null) {
                    val next = pickNext(pool.discovery, discoveryIndex, channelCount, lastChannel)
                    discoveryIndex = next.nextIndex
                    next.video
                } else {
                    null
                }
                fallbackSub.video ?: fallbackDisc
            }
            if (chosen == null) break
            selected += chosen
            val key = channelKey(chosen)
            if (key.isNotBlank()) {
                channelCount[key] = (channelCount[key] ?: 0) + 1
                lastChannel = key
            }
            if (chosen in pool.subscriptions) {
                subscriptionPicked += 1
            }
        }
        val hasMore = subIndex < pool.subscriptions.size || discoveryIndex < pool.discovery.size
        val nextCursor = if (hasMore && selected.isNotEmpty()) {
            HomeRecommendationCursorCodec.encode(
                HomeRecommendationCursor(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discoveryIndex,
                )
            )
        } else {
            null
        }
        return HomeRecommendationPage(items = selected, nextCursor = nextCursor)
    }

    private fun pickNext(
        source: List<VideoItem>,
        start: Int,
        channelCount: Map<String, Int>,
        lastChannel: String,
    ): PickResult {
        var index = start
        while (index < source.size) {
            val candidate = source[index]
            index += 1
            val channel = channelKey(candidate)
            if (channel.isBlank()) return PickResult(video = candidate, nextIndex = index)
            val count = channelCount[channel] ?: 0
            if (count >= MAX_PER_CHANNEL_PER_PAGE) continue
            if (channel == lastChannel) continue
            return PickResult(video = candidate, nextIndex = index)
        }
        return PickResult(video = null, nextIndex = index)
    }

    private fun channelKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }
}
