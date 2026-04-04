package dev.typetype.server.services

class SubscriptionShortsSignalService(private val recommendationEventService: RecommendationEventService) {
    suspend fun load(userId: String): Map<String, Double> {
        val events = recommendationEventService.getAll(userId)
        val byVideo = events
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event.eventType } }
            .groupBy({ it.first }, { it.second })
        return byVideo.mapValues { (_, types) ->
            val skip = types.count { it == "short_skip" }
            val watch = types.count { it == "watch" || it == "click" }
            when {
                skip >= 3 && watch == 0 -> 0.35
                skip >= 1 && watch == 0 -> 0.65
                else -> 1.0
            }
        }
    }
}
