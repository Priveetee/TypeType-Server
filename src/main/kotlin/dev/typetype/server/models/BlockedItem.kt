package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BlockedItem(
    val url: String,
    val blockedAt: Long = 0L,
)
