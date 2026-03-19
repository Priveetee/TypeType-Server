package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.SettingsItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

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
            )
        } ?: SettingsItem()
    }

    suspend fun upsert(userId: String, settings: SettingsItem): SettingsItem {
        DatabaseFactory.query {
            val exists = SettingsTable.selectAll().where { SettingsTable.userId eq userId }.count() > 0
            if (exists) {
                SettingsTable.update({ SettingsTable.userId eq userId }) {
                    it[defaultService] = settings.defaultService
                    it[defaultQuality] = settings.defaultQuality
                    it[autoplay] = settings.autoplay
                    it[volume] = settings.volume
                    it[muted] = settings.muted
                    it[subtitlesEnabled] = settings.subtitlesEnabled
                    it[defaultSubtitleLanguage] = settings.defaultSubtitleLanguage
                    it[defaultAudioLanguage] = settings.defaultAudioLanguage
                }
            } else {
                SettingsTable.insert {
                    it[SettingsTable.userId] = userId
                    it[defaultService] = settings.defaultService
                    it[defaultQuality] = settings.defaultQuality
                    it[autoplay] = settings.autoplay
                    it[volume] = settings.volume
                    it[muted] = settings.muted
                    it[subtitlesEnabled] = settings.subtitlesEnabled
                    it[defaultSubtitleLanguage] = settings.defaultSubtitleLanguage
                    it[defaultAudioLanguage] = settings.defaultAudioLanguage
                }
            }
        }
        return settings
    }
}
