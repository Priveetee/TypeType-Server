package dev.typetype.server.services

internal data class SabrPlaybackPreparation(
    val holder: SabrSessionHolder,
    val startTimeMs: Long,
    val ready: Boolean,
)
