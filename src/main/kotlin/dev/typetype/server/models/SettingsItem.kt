package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SettingsItem(
    val defaultService: Int = 0,
    val defaultQuality: String = "1080p",
    val autoplay: Boolean = true,
    val volume: Double = 1.0,
    val muted: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val defaultSubtitleLanguage: String = "",
    val defaultAudioLanguage: String = "",
    val preferOriginalLanguage: Boolean = false,
)
