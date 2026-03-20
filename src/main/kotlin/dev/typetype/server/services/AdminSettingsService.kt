package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.models.AdminSettingsItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

private const val SETTINGS_ROW_ID = 1

class AdminSettingsService {

    suspend fun get(): AdminSettingsItem = DatabaseFactory.query {
        AdminSettingsTable.selectAll().singleOrNull()?.let {
            AdminSettingsItem(
                allowRegistration = it[AdminSettingsTable.allowRegistration],
                allowGuest = it[AdminSettingsTable.allowGuest],
                forceEmailVerification = it[AdminSettingsTable.forceEmailVerification],
            )
        } ?: AdminSettingsItem()
    }

    suspend fun upsert(item: AdminSettingsItem): AdminSettingsItem {
        DatabaseFactory.query {
            val exists = AdminSettingsTable.selectAll().count() > 0
            if (exists) {
                AdminSettingsTable.update({ AdminSettingsTable.id eq SETTINGS_ROW_ID }) {
                    it[allowRegistration] = item.allowRegistration
                    it[allowGuest] = item.allowGuest
                    it[forceEmailVerification] = item.forceEmailVerification
                }
            } else {
                AdminSettingsTable.insert {
                    it[id] = SETTINGS_ROW_ID
                    it[allowRegistration] = item.allowRegistration
                    it[allowGuest] = item.allowGuest
                    it[forceEmailVerification] = item.forceEmailVerification
                }
            }
        }
        return item
    }
}
