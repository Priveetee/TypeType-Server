package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SettingsTable : Table("settings") {
    val userId = text("user_id")
    val defaultService = integer("default_service").default(0)
    val defaultQuality = text("default_quality").default("1080p")
    val autoplay = bool("autoplay").default(true)
    val volume = double("volume").default(1.0)
    val muted = bool("muted").default(false)
    val subtitlesEnabled = bool("subtitles_enabled").default(false)
    val defaultSubtitleLanguage = text("default_subtitle_language").default("")
    val defaultAudioLanguage = text("default_audio_language").default("")
    val preferOriginalLanguage = bool("prefer_original_language").default(false)
    val recommendationPersonalizationEnabled = bool("recommendation_personalization_enabled").default(true)
    override val primaryKey = PrimaryKey(userId)
}
