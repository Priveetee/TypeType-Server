package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SponsorBlockMode
import dev.typetype.server.models.toSponsorBlockMode
import dev.typetype.server.models.withDefaultSponsorBlockCategoryActions
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

private val SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER = MapSerializer(String.serializer(), SponsorBlockMode.serializer())

class SettingsService {

    suspend fun get(userId: String): SettingsItem = DatabaseFactory.query {
        SettingsTable.selectAll().where { SettingsTable.userId eq userId }.singleOrNull()?.let {
            SettingsItem(
                defaultService = it[SettingsTable.defaultService],
                defaultQuality = it[SettingsTable.defaultQuality],
                autoplay = it[SettingsTable.autoplay],
                volume = it[SettingsTable.volume],
                muted = it[SettingsTable.muted],
                subtitlesEnabled = it[SettingsTable.subtitlesEnabled],
                defaultSubtitleLanguage = it[SettingsTable.defaultSubtitleLanguage],
                defaultAudioLanguage = it[SettingsTable.defaultAudioLanguage],
                preferOriginalLanguage = it[SettingsTable.preferOriginalLanguage],
                enableHighQualityPlayback = it[SettingsTable.enableHighQualityPlayback],
                sponsorBlockMode = it[SettingsTable.sponsorBlockMode].toSponsorBlockMode(),
                sponsorBlockCategoryActions = decodeSponsorBlockCategoryActions(it[SettingsTable.sponsorBlockCategoryActions]),
                sponsorBlockMinimumDuration = it[SettingsTable.sponsorBlockMinimumDuration],
                sponsorBlockShowCurrentSegment = it[SettingsTable.sponsorBlockShowCurrentSegment],
                sponsorBlockShowChapters = it[SettingsTable.sponsorBlockShowChapters],
                sponsorBlockShowFullVideoLabels = it[SettingsTable.sponsorBlockShowFullVideoLabels],
                sponsorBlockManualSkipOnFullVideo = it[SettingsTable.sponsorBlockManualSkipOnFullVideo],
                sponsorBlockSkipNonMusicOnlyOnMusicVideos = it[SettingsTable.sponsorBlockSkipNonMusicOnlyOnMusicVideos],
                sponsorBlockMuteInsteadOfSkip = it[SettingsTable.sponsorBlockMuteInsteadOfSkip],
                hideHomeRecommendations = it[SettingsTable.hideHomeRecommendations],
                hideRelatedVideos = it[SettingsTable.hideRelatedVideos],
                hideComments = it[SettingsTable.hideComments],
                hideShorts = it[SettingsTable.hideShorts],
            )
        } ?: SettingsItem()
    }

    suspend fun upsert(userId: String, settings: SettingsItem): SettingsItem {
        val saved = settings.normalized()
        DatabaseFactory.query {
            val updated = SettingsTable.update({ SettingsTable.userId eq userId }) {
                it[defaultService] = saved.defaultService
                it[defaultQuality] = saved.defaultQuality
                it[autoplay] = saved.autoplay
                it[volume] = saved.volume
                it[muted] = saved.muted
                it[subtitlesEnabled] = saved.subtitlesEnabled
                it[defaultSubtitleLanguage] = saved.defaultSubtitleLanguage
                it[defaultAudioLanguage] = saved.defaultAudioLanguage
                it[preferOriginalLanguage] = saved.preferOriginalLanguage
                it[enableHighQualityPlayback] = saved.enableHighQualityPlayback
                it[sponsorBlockMode] = saved.sponsorBlockMode.storageValue
                it[sponsorBlockCategoryActions] = encodeSponsorBlockCategoryActions(saved.sponsorBlockCategoryActions)
                it[sponsorBlockMinimumDuration] = saved.sponsorBlockMinimumDuration
                it[sponsorBlockShowCurrentSegment] = saved.sponsorBlockShowCurrentSegment
                it[sponsorBlockShowChapters] = saved.sponsorBlockShowChapters
                it[sponsorBlockShowFullVideoLabels] = saved.sponsorBlockShowFullVideoLabels
                it[sponsorBlockManualSkipOnFullVideo] = saved.sponsorBlockManualSkipOnFullVideo
                it[sponsorBlockSkipNonMusicOnlyOnMusicVideos] = saved.sponsorBlockSkipNonMusicOnlyOnMusicVideos
                it[sponsorBlockMuteInsteadOfSkip] = saved.sponsorBlockMuteInsteadOfSkip
                it[hideHomeRecommendations] = saved.hideHomeRecommendations
                it[hideRelatedVideos] = saved.hideRelatedVideos
                it[hideComments] = saved.hideComments
                it[hideShorts] = saved.hideShorts
            }
            if (updated == 0) {
                SettingsTable.insert {
                    it[SettingsTable.userId] = userId
                    it[defaultService] = saved.defaultService
                    it[defaultQuality] = saved.defaultQuality
                    it[autoplay] = saved.autoplay
                    it[volume] = saved.volume
                    it[muted] = saved.muted
                    it[subtitlesEnabled] = saved.subtitlesEnabled
                    it[defaultSubtitleLanguage] = saved.defaultSubtitleLanguage
                    it[defaultAudioLanguage] = saved.defaultAudioLanguage
                    it[preferOriginalLanguage] = saved.preferOriginalLanguage
                    it[enableHighQualityPlayback] = saved.enableHighQualityPlayback
                    it[sponsorBlockMode] = saved.sponsorBlockMode.storageValue
                    it[sponsorBlockCategoryActions] = encodeSponsorBlockCategoryActions(saved.sponsorBlockCategoryActions)
                    it[sponsorBlockMinimumDuration] = saved.sponsorBlockMinimumDuration
                    it[sponsorBlockShowCurrentSegment] = saved.sponsorBlockShowCurrentSegment
                    it[sponsorBlockShowChapters] = saved.sponsorBlockShowChapters
                    it[sponsorBlockShowFullVideoLabels] = saved.sponsorBlockShowFullVideoLabels
                    it[sponsorBlockManualSkipOnFullVideo] = saved.sponsorBlockManualSkipOnFullVideo
                    it[sponsorBlockSkipNonMusicOnlyOnMusicVideos] = saved.sponsorBlockSkipNonMusicOnlyOnMusicVideos
                    it[sponsorBlockMuteInsteadOfSkip] = saved.sponsorBlockMuteInsteadOfSkip
                    it[hideHomeRecommendations] = saved.hideHomeRecommendations
                    it[hideRelatedVideos] = saved.hideRelatedVideos
                    it[hideComments] = saved.hideComments
                    it[hideShorts] = saved.hideShorts
                }
            }
        }
        return saved
    }
}

private fun SettingsItem.normalized(): SettingsItem =
    copy(sponsorBlockCategoryActions = sponsorBlockCategoryActions.withDefaultSponsorBlockCategoryActions(), sponsorBlockMinimumDuration = sponsorBlockMinimumDuration.coerceAtLeast(0))

private fun decodeSponsorBlockCategoryActions(raw: String): Map<String, SponsorBlockMode> =
    runCatching { CacheJson.decodeFromString(SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER, raw) }.getOrElse { DEFAULT_SPONSOR_BLOCK_CATEGORY_ACTIONS }.withDefaultSponsorBlockCategoryActions()

private fun encodeSponsorBlockCategoryActions(actions: Map<String, SponsorBlockMode>): String =
    CacheJson.encodeToString(SPONSOR_BLOCK_CATEGORY_ACTIONS_SERIALIZER, actions)
