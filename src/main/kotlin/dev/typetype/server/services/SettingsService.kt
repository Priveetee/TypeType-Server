package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.SettingsItem
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class SettingsService {

    suspend fun get(): SettingsItem = DatabaseFactory.query {
        SettingsTable.selectAll().singleOrNull()?.let {
            SettingsItem(
                defaultService = it[SettingsTable.defaultService],
                defaultQuality = it[SettingsTable.defaultQuality],
                autoplay = it[SettingsTable.autoplay],
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
                }
            } else {
                SettingsTable.insert {
                    it[id] = 1
                    it[defaultService] = settings.defaultService
                    it[defaultQuality] = settings.defaultQuality
                    it[autoplay] = settings.autoplay
                }
            }
        }
        return settings
    }
}
