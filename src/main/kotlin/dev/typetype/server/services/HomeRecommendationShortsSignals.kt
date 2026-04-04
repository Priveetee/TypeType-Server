package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationShortsSignals {
    fun shortSkipPenaltyByUrl(events: List<RecommendationEventItem>): Map<String, Double> {
        if (events.isEmpty()) return emptyMap()
        val grouped = events
            .take(500)
            .asSequence()
            .filter { it.videoUrl != null }
            .groupBy({ it.videoUrl!! }, { it })
        return grouped.mapValues { (_, list) ->
            val quickSkips = list.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 0L..1_000L }
            val normalSkips = list.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 1_001L..5_000L }
            val deepWatches = list.count { it.eventType == "watch" && (it.watchRatio ?: 0.0) >= 0.75 }
            when {
                quickSkips >= 2 && deepWatches == 0 -> 0.30
                quickSkips + normalSkips >= 2 && deepWatches == 0 -> 0.55
                quickSkips + normalSkips >= 1 -> 0.80
                else -> 1.0
            }
        }
    }
}
