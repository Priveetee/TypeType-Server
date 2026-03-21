package dev.typetype.server.services

object HomeRecommendationLiveTitleDetector {
    private val liveMarkers = listOf(
        " live",
        "is live",
        "en direct",
        "direct:",
        "livestream",
        "live:",
        "streamed",
    )

    fun isLiveLike(title: String): Boolean {
        val normalized = title.lowercase()
        return liveMarkers.any { marker -> marker in normalized }
    }
}
