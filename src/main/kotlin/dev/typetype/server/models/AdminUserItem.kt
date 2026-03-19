package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserItem(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val suspended: Boolean,
    val verified: Boolean,
    val createdAt: Long,
)
