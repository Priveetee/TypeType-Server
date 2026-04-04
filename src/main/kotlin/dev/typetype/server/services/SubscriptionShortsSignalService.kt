package dev.typetype.server.services

class SubscriptionShortsSignalService(private val recommendationEventService: RecommendationEventService) {
    suspend fun load(userId: String): Map<String, Double> {
        val events = recommendationEventService.getAll(userId)
        val byVideo = events
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event } }
            .groupBy({ it.first }, { it.second })
        return byVideo.mapValues { (_, items) ->
            val skipLight = items.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 800L..4_999L }
            val skipInstant = items.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) in 0L..799L }
            val skipLate = items.count { it.eventType == "short_skip" && (it.watchDurationMs ?: 0L) >= 5_000L }
            val watch = items.count { it.eventType == "watch" || it.eventType == "click" }
            val skipScore = skipInstant * 1.2 + skipLight * 0.9 + skipLate * 0.4
            when {
                skipScore >= 3.0 && watch == 0 -> 0.35
                skipScore >= 1.0 && watch == 0 -> 0.65
                else -> 1.0
            }
        }
    }
}
