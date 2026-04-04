package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

object HomeRecommendationMixer {
    fun mix(pool: HomeRecommendationPool, cursor: HomeRecommendationCursor, limit: Int): HomeRecommendationPage {
        val planner = HomeRecommendationQuotaPlanner(
            limit = limit,
            subscriptionSize = pool.subscriptions.size,
            discoverySize = pool.discovery.size,
        )
        val machine = HomeRecommendationStateMachine(planner)
        val selected = mutableListOf<VideoItem>()
        val channelCount = mutableMapOf<String, Int>()
        var lastChannel = ""
        var subIndex = cursor.subscriptionIndex
        var discoveryIndex = cursor.discoveryIndex
        var subscriptionRun = cursor.subscriptionRun.coerceIn(0, HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN)
        var preferDiscovery = cursor.preferDiscovery
        var subscriptionCount = 0
        var discoveryCount = 0
        while (selected.size < limit) {
            val decision = machine.decide(
                subscriptionCount = subscriptionCount,
                discoveryCount = discoveryCount,
                selected = selected.size,
                subscriptionRun = subscriptionRun,
                preferDiscovery = preferDiscovery,
            )
            val picker = HomeRecommendationPicker(
                pool = pool,
                channelCount = channelCount,
                lastChannel = lastChannel,
            )
            val selection = HomeRecommendationSelector.pick(
                picker = picker,
                wantDiscovery = decision.wantDiscovery,
                subIndex = subIndex,
                discoveryIndex = discoveryIndex,
            )
            if (selection == null) {
                break
            }
            val video = selection.video
            val state = selection.state
            subIndex = state.subscriptionIndex
            discoveryIndex = state.discoveryIndex
            if (isSubscriptionVideo(video, pool)) {
                subscriptionCount += 1
                subscriptionRun = (subscriptionRun + 1)
                    .coerceAtMost(HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN)
                if (subscriptionRun >= HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN) {
                    preferDiscovery = true
                }
            } else {
                discoveryCount += 1
                subscriptionRun = 0
                preferDiscovery = false
            }
            selected += video
            val key = channelKey(video)
            if (key.isNotBlank()) {
                channelCount[key] = (channelCount[key] ?: 0) + 1
                lastChannel = key
            }
        }
        val hasMore = subIndex < pool.subscriptions.size || discoveryIndex < pool.discovery.size
        val nextCursor = if (hasMore && selected.isNotEmpty()) {
            HomeRecommendationCursorCodec.encode(
                HomeRecommendationCursor(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discoveryIndex,
                    subscriptionRun = subscriptionRun,
                    preferDiscovery = preferDiscovery,
                ),
            )
        } else {
            null
        }
        return HomeRecommendationPage(
            items = selected,
            nextCursor = nextCursor,
            subscriptionCount = subscriptionCount,
            discoveryCount = discoveryCount,
        )
    }

    private fun channelKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }

    private fun isSubscriptionVideo(video: VideoItem, pool: HomeRecommendationPool): Boolean {
        if (video in pool.subscriptions) return true
        return video.uploaderUrl.isNotBlank() && video.uploaderUrl in pool.subscriptionChannels
    }
}
