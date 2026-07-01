package dev.typetype.server.services

internal data class SabrSessionKey(
    val videoId: String,
    val userId: String,
    val audioItag: Int,
    val videoItag: Int,
)
