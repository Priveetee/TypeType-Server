package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationCursorPayload(
    val s: Int,
    val d: Int,
    val r: Int,
    val p: Int,
    val c: List<String> = emptyList(),
    val k: List<String> = emptyList(),
    val m: Map<String, Int> = emptyMap(),
    val o: Map<String, Long> = emptyMap(),
    val t: List<String> = emptyList(),
)
