package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class InstanceResponse(
    val name: String,
    val tagline: String? = null,
    val version: String,
    val revision: String,
    val shortRevision: String,
    val buildTime: String,
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
    val youtubeRemoteLoginReady: Boolean = false,
    val youtubeRemoteLoginUnavailableReason: String? = null,
    val androidPlayback: AndroidPlaybackCapability = AndroidPlaybackCapability(),
)

@Serializable
data class AndroidPlaybackCapability(
    val supported: Boolean = true,
    val contractVersion: Int = 4,
    val youtube: AndroidYoutubePlaybackCapability = AndroidYoutubePlaybackCapability(),
)

@Serializable
data class AndroidYoutubePlaybackCapability(
    val vod: Boolean = true,
    val live: Boolean = false,
    val subtitles: Boolean = true,
    val deferredSubtitleContent: Boolean = true,
    val bootstrapSubtitleDescriptors: Boolean = true,
)
