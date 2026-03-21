package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationCursorPayload(
    val s: Int,
    val d: Int,
    val r: Int,
    val p: Int,
)
