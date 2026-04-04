package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

object HomeRecommendationSelector {
    fun pick(
        picker: HomeRecommendationPicker,
        wantDiscovery: Boolean,
        forceNovelty: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): HomeRecommendationSelection? {
        val pick = if (wantDiscovery) {
            pickDiscoveryFirst(picker, forceNovelty, subIndex, discoveryIndex)
        } else {
            pickSubscriptionFirst(picker, subIndex, discoveryIndex)
        }
        val video = pick.first ?: return null
        return HomeRecommendationSelection(video = video.first, state = video.second, isNovelty = pick.second)
    }

    private fun pickDiscoveryFirst(
        picker: HomeRecommendationPicker,
        forceNovelty: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<Pair<VideoItem, HomeRecommendationCursorState>?, Boolean> {
        val discovery = picker.fromDiscovery(discoveryIndex, forceNovelty)
        val discoveryVideo = discovery.first
        if (discoveryVideo != null) {
            return (
                discoveryVideo to HomeRecommendationCursorState(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discovery.second,
                    subscriptionRun = 0,
                    preferDiscovery = false,
                )
            )
                .let { it to true }
        }
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first ?: return null to false
        return (
            subscriptionVideo to HomeRecommendationCursorState(
                subscriptionIndex = subscription.second,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 1,
                preferDiscovery = false,
            )
        )
            .let { it to false }
    }

    private fun pickSubscriptionFirst(
        picker: HomeRecommendationPicker,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<Pair<VideoItem, HomeRecommendationCursorState>?, Boolean> {
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first
        if (subscriptionVideo != null) {
            return (
                subscriptionVideo to HomeRecommendationCursorState(
                    subscriptionIndex = subscription.second,
                    discoveryIndex = discoveryIndex,
                    subscriptionRun = 1,
                    preferDiscovery = false,
                )
            )
                .let { it to false }
        }
        val discovery = picker.fromDiscovery(discoveryIndex)
        val discoveryVideo = discovery.first ?: return null to false
        return (
            discoveryVideo to HomeRecommendationCursorState(
                subscriptionIndex = subIndex,
                discoveryIndex = discovery.second,
                subscriptionRun = 0,
                preferDiscovery = false,
            )
        )
            .let { it to true }
    }
}
