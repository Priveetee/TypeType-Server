package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem

object HomeRecommendationMixer {
    fun mix(
        pool: HomeRecommendationPool,
        cursor: HomeRecommendationCursor,
        limit: Int,
        context: HomeRecommendationSessionContext,
        sourceWeights: Map<HomeRecommendationSourceTag, Double> = emptyMap(),
    ): HomeRecommendationPage {
        val planner = HomeRecommendationQuotaPlanner(
            limit = limit,
            subscriptionSize = pool.subscriptions.size,
            discoverySize = pool.discovery.size,
            sourceByUrl = pool.sourceByUrl,
            sourceWeights = sourceWeights,
            sessionContext = context,
            personaState = cursor.personaState,
        )
        val machine = HomeRecommendationStateMachine(planner)
        val selected = mutableListOf<VideoItem>()
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
        while (selected.size < limit) {
            val decision = machine.decide(
                subscriptionCount = subscriptionCount,
                discoveryCount = discoveryCount,
                noveltyCount = noveltyCount,
                selected = selected.size,
                subscriptionRun = subscriptionRun,
                preferDiscovery = preferDiscovery,
            )
            val picker = HomeRecommendationPicker(
                pool = pool,
                channelCount = channelCount,
                recentChannels = memory.recentChannelsSet(),
                recentSemanticKeys = memory.recentSemanticKeysSet(),
                creatorMomentum = memory.creatorMomentumMap(),
                creatorCooldownUntilMs = memory.creatorCooldownMap(),
                recentTopicPairs = memory.recentTopicPairsSet(),
                recentUrls = memory.recentUrlsSet(),
            )
            val selection = HomeRecommendationSelector.pick(
                picker = picker,
                wantDiscovery = decision.wantDiscovery,
                forceNovelty = decision.forceNovelty,
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
            if (selection.isNovelty) noveltyCount += 1
            personaState = HomeRecommendationPersonaDrift.onSelected(personaState, video)
            val key = channelKey(video)
            if (key.isNotBlank()) channelCount[key] = (channelCount[key] ?: 0) + 1
            memory.onSelected(video.title, key)
            memory.onSelectedUrl(video.url)
        }
        val hasMore = subIndex < pool.subscriptions.size || discoveryIndex < pool.discovery.size
        val nextCursor = if (hasMore && selected.isNotEmpty()) {
            val snapshot = memory.snapshot()
            HomeRecommendationCursorCodec.encode(
                HomeRecommendationCursor(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discoveryIndex,
                    subscriptionRun = subscriptionRun,
                    preferDiscovery = preferDiscovery,
                    recentChannels = snapshot.recentChannels,
                    recentSemanticKeys = snapshot.recentSemanticKeys,
                    creatorMomentum = snapshot.creatorMomentum,
                    creatorCooldownUntilMs = snapshot.creatorCooldownUntilMs,
                    recentTopicPairs = snapshot.recentTopicPairs,
                    recentUrls = snapshot.recentUrls,
                    personaState = personaState,
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
