package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class InstanceResponse(
    val name: String,
    val tagline: String? = null,
    val version: String,
    val apiVersion: Int,
    val registrationAllowed: Boolean,
    val guestAllowed: Boolean,
    val supportedServices: List<Int>,
    val minClientVersion: InstanceMinClientVersion = InstanceMinClientVersion(),
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val localLoginEnabled: Boolean = true,
    val oidcEnabled: Boolean = false,
    val oidcProviderName: String? = null,
    val oidcAutoRedirect: Boolean = false,
    val youtubeRemoteLoginEnabled: Boolean = false,
)
