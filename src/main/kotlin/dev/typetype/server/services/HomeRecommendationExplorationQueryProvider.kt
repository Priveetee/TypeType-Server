package dev.typetype.server.services

object HomeRecommendationExplorationQueryProvider {
    fun queries(mode: HomeRecommendationPoolMode): List<String> {
        val limit = if (mode == HomeRecommendationPoolMode.FAST) FAST_QUERY_COUNT else FULL_QUERY_COUNT
        return DEFAULT_QUERIES.take(limit)
    }

    private val DEFAULT_QUERIES = listOf(
        "trending videos",
        "viral videos",
        "new uploads",
        "breaking news",
        "technology",
        "gaming",
        "music",
        "documentary",
    )

    private const val FAST_QUERY_COUNT = 2
    private const val FULL_QUERY_COUNT = 6
}
