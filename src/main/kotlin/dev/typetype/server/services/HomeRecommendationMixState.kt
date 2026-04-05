package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

class HomeRecommendationMixState(cursor: HomeRecommendationCursor, context: HomeRecommendationSessionContext) {
    val channelCount = mutableMapOf<String, Int>()
    val memory = HomeRecommendationMomentumMemory(cursor)
    var personaState = HomeRecommendationPersonaDrift.seed(context, cursor.personaState)
    var subIndex = cursor.subscriptionIndex
    var discoveryIndex = cursor.discoveryIndex
    var subscriptionRun = cursor.subscriptionRun.coerceIn(0, HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN)
    var preferDiscovery = cursor.preferDiscovery
    var subscriptionCount = 0
    var discoveryCount = 0
    var noveltyCount = 0

    fun onSelected(video: VideoItem, state: HomeRecommendationCursorState, isNovelty: Boolean, pool: HomeRecommendationPool) {
        subIndex = state.subscriptionIndex
        discoveryIndex = state.discoveryIndex
        if (isSubscriptionVideo(video, pool)) {
            subscriptionCount += 1
            subscriptionRun = (subscriptionRun + 1).coerceAtMost(HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN)
            if (subscriptionRun >= HomeRecommendationQuotaPlanner.MAX_SUBSCRIPTION_RUN) preferDiscovery = true
        } else {
            discoveryCount += 1
            subscriptionRun = 0
            preferDiscovery = false
        }
        if (isNovelty) noveltyCount += 1
        personaState = HomeRecommendationPersonaDrift.onSelected(personaState, video)
        val key = channelKey(video)
        if (key.isNotBlank()) channelCount[key] = (channelCount[key] ?: 0) + 1
        memory.onSelected(video.title, key)
        memory.onSelectedUrl(video.url)
    }

    private fun channelKey(video: VideoItem): String = video.uploaderUrl.ifBlank { video.uploaderName }

    private fun isSubscriptionVideo(video: VideoItem, pool: HomeRecommendationPool): Boolean {
        if (video in pool.subscriptions) return true
        return video.uploaderUrl.isNotBlank() && video.uploaderUrl in pool.subscriptionChannels
    }
}
