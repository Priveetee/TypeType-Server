package dev.typetype.server.services

import dev.typetype.server.models.RecommendationEventItem

object HomeRecommendationEventAnalyzer {
    fun buildSignals(events: List<RecommendationEventItem>): HomeRecommendationEngagementSignals {
        if (events.isEmpty()) return HomeRecommendationEngagementSignals(emptyMap(), emptySet())
        val now = System.currentTimeMillis()
        val byVideo = events
            .asSequence()
            .mapNotNull { event -> event.videoUrl?.takeIf { it.isNotBlank() }?.let { it to event } }
            .groupBy({ it.first }, { it.second })
        val penalties = mutableMapOf<String, Double>()
        val blocked = mutableSetOf<String>()
        byVideo.forEach { (videoUrl, videoEvents) ->
            val clicks = videoEvents.count { it.eventType == "click" || it.eventType == "watch" }
            val watch = videoEvents.firstOrNull { it.eventType == "watch" }
            val watchRatio = watch?.watchRatio ?: 0.0
            val recentImpressions = videoEvents.count {
                it.eventType == "impression" && (now - it.occurredAt) <= IMPLICIT_WINDOW_MS
            }
            if (clicks == 0 && recentImpressions >= BLOCK_THRESHOLD) {
                blocked += videoUrl
                penalties[videoUrl] = HEAVY_PENALTY
            } else if (clicks == 0 && recentImpressions >= LIGHT_THRESHOLD) {
                penalties[videoUrl] = MEDIUM_PENALTY
            } else if (watchRatio > 0.0 && watchRatio < 0.15) {
                penalties[videoUrl] = SHORT_WATCH_PENALTY
            }
        }
        return HomeRecommendationEngagementSignals(videoPenalty = penalties, implicitBlockedVideos = blocked)
    }

    private const val IMPLICIT_WINDOW_MS = 48L * 60L * 60L * 1000L
    private const val BLOCK_THRESHOLD = 5
    private const val LIGHT_THRESHOLD = 3
    private const val HEAVY_PENALTY = 0.10
    private const val MEDIUM_PENALTY = 0.30
    private const val SHORT_WATCH_PENALTY = 0.45
}
