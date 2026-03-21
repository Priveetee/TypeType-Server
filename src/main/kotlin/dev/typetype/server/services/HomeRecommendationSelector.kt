package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationSelector {
    fun pick(
        picker: HomeRecommendationPicker,
        wantDiscovery: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): HomeRecommendationSelection? {
        val pick = if (wantDiscovery) {
            pickDiscoveryFirst(picker, subIndex, discoveryIndex)
        } else {
            pickSubscriptionFirst(picker, subIndex, discoveryIndex)
        }
        val video = pick.first ?: return null
        return HomeRecommendationSelection(video = video, state = pick.second)
    }

    private fun pickDiscoveryFirst(
        picker: HomeRecommendationPicker,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<VideoItem?, HomeRecommendationCursorState> {
        val discovery = picker.fromDiscovery(discoveryIndex)
        if (discovery.first != null) {
            return discovery.first to HomeRecommendationCursorState(
                subscriptionIndex = subIndex,
                discoveryIndex = discovery.second,
                subscriptionRun = 0,
                preferDiscovery = false,
            )
        }
        val subscription = picker.fromSubscriptions(subIndex)
        return subscription.first to HomeRecommendationCursorState(
            subscriptionIndex = subscription.second,
            discoveryIndex = discoveryIndex,
            subscriptionRun = 1,
            preferDiscovery = false,
        )
    }

    private fun pickSubscriptionFirst(
        picker: HomeRecommendationPicker,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<VideoItem?, HomeRecommendationCursorState> {
        val subscription = picker.fromSubscriptions(subIndex)
        if (subscription.first != null) {
            return subscription.first to HomeRecommendationCursorState(
                subscriptionIndex = subscription.second,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 1,
                preferDiscovery = false,
            )
        }
        val discovery = picker.fromDiscovery(discoveryIndex)
        return discovery.first to HomeRecommendationCursorState(
            subscriptionIndex = subIndex,
            discoveryIndex = discovery.second,
            subscriptionRun = 0,
            preferDiscovery = false,
        )
    }
}
