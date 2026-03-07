package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SettingsItem(
    val defaultService: Int = 0,
    val defaultQuality: String = "1080p",
    val autoplay: Boolean = true,
)
