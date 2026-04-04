package dev.typetype.server.services

import dev.typetype.server.models.VideoItem
import kotlin.math.log2

object HomeRecommendationOfflineEvaluator {
    fun evaluate(
        ranked: List<VideoItem>,
        clickedUrls: Set<String>,
    ): HomeRecommendationMetrics {
        val top = ranked.take(10)
        if (top.isEmpty()) {
            return HomeRecommendationMetrics(ndcgAt10 = 0.0, diversityAt10 = 0.0, duplicateRateAt10 = 0.0)
        }
        val dcg = top.mapIndexed { index, video ->
            val rel = if (video.url in clickedUrls) 1.0 else 0.0
            rel / log2((index + 2).toDouble())
        }.sum()
        val idealCount = minOf(10, clickedUrls.size)
        val idcg = (0 until idealCount).sumOf { index -> 1.0 / log2((index + 2).toDouble()) }
        val ndcg = if (idcg <= 0.0) 0.0 else (dcg / idcg).coerceIn(0.0, 1.0)
        val uniqueChannels = top.map { it.uploaderUrl.ifBlank { it.uploaderName } }.toSet().size
        val diversity = uniqueChannels.toDouble() / top.size.toDouble()
        val duplicates = top.size - top.map { it.url }.toSet().size
        val duplicateRate = duplicates.toDouble() / top.size.toDouble()
        return HomeRecommendationMetrics(
            ndcgAt10 = ndcg,
            diversityAt10 = diversity,
            duplicateRateAt10 = duplicateRate,
        )
    }
}
