package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.SettingsItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SettingsService {

    suspend fun get(): SettingsItem = DatabaseFactory.query {
        SettingsTable.selectAll().singleOrNull()?.let {
            SettingsItem(
                defaultService = it[SettingsTable.defaultService],
                defaultQuality = it[SettingsTable.defaultQuality],
                autoplay = it[SettingsTable.autoplay],
                volume = it[SettingsTable.volume],
                muted = it[SettingsTable.muted],
            )
        } ?: SettingsItem()
    }

    suspend fun upsert(settings: SettingsItem): SettingsItem {
        DatabaseFactory.query {
            val exists = SettingsTable.selectAll().count() > 0
            if (exists) {
                SettingsTable.update({ SettingsTable.id eq 1 }) {
                    it[defaultService] = settings.defaultService
                    it[defaultQuality] = settings.defaultQuality
                    it[autoplay] = settings.autoplay
                    it[volume] = settings.volume
                    it[muted] = settings.muted
                }
            } else {
                SettingsTable.insert {
                    it[id] = 1
                    it[defaultService] = settings.defaultService
                    it[defaultQuality] = settings.defaultQuality
                    it[autoplay] = settings.autoplay
                    it[volume] = settings.volume
                    it[muted] = settings.muted
                }
            }
        }
        return settings
    }
}
