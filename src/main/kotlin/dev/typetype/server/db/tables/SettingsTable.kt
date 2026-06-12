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
    val enableHighQualityPlayback = bool("enable_high_quality_playback").default(false)
    val sponsorBlockMode = text("sponsor_block_mode").default("auto_skip")
    val hideHomeRecommendations = bool("hide_home_recommendations").default(false)
    val hideRelatedVideos = bool("hide_related_videos").default(false)
    val hideComments = bool("hide_comments").default(false)
    val hideShorts = bool("hide_shorts").default(false)
    override val primaryKey = PrimaryKey(userId)
}
