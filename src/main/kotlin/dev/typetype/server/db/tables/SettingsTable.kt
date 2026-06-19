package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SettingsTable : Table("settings") {
    val userId = text("user_id")
    val defaultService = integer("default_service").default(0)
    val defaultQuality = text("default_quality").default("1080p")
    val defaultLandingPage = text("default_landing_page").default("home")
    val autoplay = bool("autoplay").default(true)
    val volume = double("volume").default(1.0)
    val muted = bool("muted").default(false)
    val subtitlesEnabled = bool("subtitles_enabled").default(false)
    val defaultSubtitleLanguage = text("default_subtitle_language").default("")
    val defaultAudioLanguage = text("default_audio_language").default("")
    val captionStyles = text("caption_styles").default("{}")
    val preferOriginalLanguage = bool("prefer_original_language").default(false)
    val enableHighQualityPlayback = bool("enable_high_quality_playback").default(false)
    val sponsorBlockMode = text("sponsor_block_mode").default("auto_skip")
    val sponsorBlockCategoryActions = text("sponsor_block_category_actions").default("{}")
    val sponsorBlockMinimumDuration = integer("sponsor_block_minimum_duration").default(0)
    val sponsorBlockShowCurrentSegment = bool("sponsor_block_show_current_segment").default(true)
    val sponsorBlockShowChapters = bool("sponsor_block_show_chapters").default(false)
    val sponsorBlockShowFullVideoLabels = bool("sponsor_block_show_full_video_labels").default(true)
    val sponsorBlockManualSkipOnFullVideo = bool("sponsor_block_manual_skip_on_full_video").default(true)
    val sponsorBlockSkipNonMusicOnlyOnMusicVideos = bool("sponsor_block_skip_non_music_only_on_music_videos").default(false)
    val sponsorBlockMuteInsteadOfSkip = bool("sponsor_block_mute_instead_of_skip").default(false)
    val hideHomeRecommendations = bool("hide_home_recommendations").default(false)
    val hideContinueWatching = bool("hide_continue_watching").default(false)
    val hideRelatedVideos = bool("hide_related_videos").default(false)
    val hideComments = bool("hide_comments").default(false)
    val hideShorts = bool("hide_shorts").default(false)
    override val primaryKey = PrimaryKey(userId)
}
