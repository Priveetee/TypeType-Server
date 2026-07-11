package dev.typetype.server.services

internal data class SabrSessionKey(
    val videoId: String,
    val userId: String,
    val audioItag: Int,
    val audioTrackId: String?,
    val videoItag: Int,
    val startTimeMs: Long,
    val purpose: SabrSessionPurpose = SabrSessionPurpose.MANIFEST,
)
