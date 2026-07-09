package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class VersionResponse(
    val version: String,
    val revision: String,
    val shortRevision: String,
    val buildTime: String,
)
