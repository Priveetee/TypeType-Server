package dev.typetype.server.services

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
        val selection = pick.first ?: return null
        return HomeRecommendationSelection(
            video = selection.video,
            state = selection.state,
            isNovelty = pick.second,
            source = selection.source,
        )
    }

    private fun pickDiscoveryFirst(
        picker: HomeRecommendationPicker,
        forceNovelty: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<HomeRecommendationPickState?, Boolean> {
        val discovery = picker.fromDiscovery(discoveryIndex, forceNovelty)
        val discoveryVideo = discovery.first
        if (discoveryVideo != null) {
            return HomeRecommendationPickState(
                video = discoveryVideo,
                state = HomeRecommendationCursorState(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discovery.second,
                    subscriptionRun = 0,
                    preferDiscovery = false,
                ),
                source = picker.sourceOf(discoveryVideo),
            )
                .let { it to true }
        }
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first ?: return null to false
        return HomeRecommendationPickState(
            video = subscriptionVideo,
            state = HomeRecommendationCursorState(
                subscriptionIndex = subscription.second,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 1,
                preferDiscovery = false,
            ),
            source = picker.sourceOf(subscriptionVideo),
        )
            .let { it to false }
    }

    private fun pickSubscriptionFirst(
        picker: HomeRecommendationPicker,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<HomeRecommendationPickState?, Boolean> {
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first
        if (subscriptionVideo != null) {
            return HomeRecommendationPickState(
                video = subscriptionVideo,
                state = HomeRecommendationCursorState(
                    subscriptionIndex = subscription.second,
                    discoveryIndex = discoveryIndex,
                    subscriptionRun = 1,
                    preferDiscovery = false,
                ),
                source = picker.sourceOf(subscriptionVideo),
            )
                .let { it to false }
        }
        val discovery = picker.fromDiscovery(discoveryIndex)
        val discoveryVideo = discovery.first ?: return null to false
        return HomeRecommendationPickState(
            video = discoveryVideo,
            state = HomeRecommendationCursorState(
                subscriptionIndex = subIndex,
                discoveryIndex = discovery.second,
                subscriptionRun = 0,
                preferDiscovery = false,
            ),
            source = picker.sourceOf(discoveryVideo),
        )
            .let { it to true }
    }
}
