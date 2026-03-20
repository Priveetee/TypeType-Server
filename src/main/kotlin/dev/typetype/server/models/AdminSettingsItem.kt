package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminSettingsItem(
    val allowRegistration: Boolean = true,
    val allowGuest: Boolean = true,
    val forceEmailVerification: Boolean = false,
)
