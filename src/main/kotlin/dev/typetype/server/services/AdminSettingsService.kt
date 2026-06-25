package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.DEFAULT_INSTANCE_NAME
import dev.typetype.server.models.AdminSettingsItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

private const val SETTINGS_ROW_ID = 1

class AdminSettingsService(
    private val defaultSettings: () -> AdminSettingsItem = ::defaultAdminSettingsFromEnvironment,
) {

    suspend fun get(): AdminSettingsItem {
        cachedSettings?.let { return it }
        val settings = DatabaseFactory.query {
            AdminSettingsTable.selectAll().singleOrNull()?.let {
                AdminSettingsItem(
                    name = it[AdminSettingsTable.name],
                    tagline = it[AdminSettingsTable.tagline],
                    logoUrl = it[AdminSettingsTable.logoUrl],
                    bannerUrl = it[AdminSettingsTable.bannerUrl],
                    minAndroidClientVersion = it[AdminSettingsTable.minAndroidClientVersion],
                    allowRegistration = it[AdminSettingsTable.allowRegistration],
                    allowGuest = it[AdminSettingsTable.allowGuest],
                    forceEmailVerification = it[AdminSettingsTable.forceEmailVerification],
                    activeSessionsEnabled = it[AdminSettingsTable.activeSessionsEnabled],
                    localLoginEnabled = it[AdminSettingsTable.localLoginEnabled],
                    oidcAutoRedirect = it[AdminSettingsTable.oidcAutoRedirect],
                    youtubeRemoteLoginEnabled = it[AdminSettingsTable.youtubeRemoteLoginEnabled],
                    accessMode = it[AdminSettingsTable.accessMode].toAccessMode(),
                ).normalized()
            } ?: defaultSettings().normalized()
        }
        cachedSettings = settings
        return settings
    }

    suspend fun upsert(item: AdminSettingsItem): AdminSettingsItem {
        val settings = item.normalized()
        DatabaseFactory.query {
            val exists = AdminSettingsTable.selectAll().count() > 0
            if (exists) {
                AdminSettingsTable.update({ AdminSettingsTable.id eq SETTINGS_ROW_ID }) {
                    it[name] = settings.name
                    it[tagline] = settings.tagline
                    it[logoUrl] = settings.logoUrl
                    it[bannerUrl] = settings.bannerUrl
                    it[minAndroidClientVersion] = settings.minAndroidClientVersion
                    it[allowRegistration] = settings.allowRegistration
                    it[allowGuest] = settings.allowGuest
                    it[forceEmailVerification] = settings.forceEmailVerification
                    it[activeSessionsEnabled] = settings.activeSessionsEnabled
                    it[localLoginEnabled] = settings.localLoginEnabled
                    it[oidcAutoRedirect] = settings.oidcAutoRedirect
                    it[youtubeRemoteLoginEnabled] = settings.youtubeRemoteLoginEnabled
                    it[accessMode] = settings.accessMode.toAccessMode()
                }
            } else {
                AdminSettingsTable.insert {
                    it[id] = SETTINGS_ROW_ID
                    it[name] = settings.name
                    it[tagline] = settings.tagline
                    it[logoUrl] = settings.logoUrl
                    it[bannerUrl] = settings.bannerUrl
                    it[minAndroidClientVersion] = settings.minAndroidClientVersion
                    it[allowRegistration] = settings.allowRegistration
                    it[allowGuest] = settings.allowGuest
                    it[forceEmailVerification] = settings.forceEmailVerification
                    it[activeSessionsEnabled] = settings.activeSessionsEnabled
                    it[localLoginEnabled] = settings.localLoginEnabled
                    it[oidcAutoRedirect] = settings.oidcAutoRedirect
                    it[youtubeRemoteLoginEnabled] = settings.youtubeRemoteLoginEnabled
                    it[accessMode] = settings.accessMode.toAccessMode()
                }
            }
        }
        cachedSettings = settings
        return settings
    }

    private fun AdminSettingsItem.normalized(): AdminSettingsItem = copy(
        name = name.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_INSTANCE_NAME,
        tagline = tagline.normalizeOptionalText(),
        logoUrl = logoUrl.normalizeOptionalText(),
        bannerUrl = bannerUrl.normalizeOptionalText(),
        minAndroidClientVersion = minAndroidClientVersion.normalizeOptionalText(),
        accessMode = accessMode.toAccessMode(),
    )

    private fun String?.normalizeOptionalText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        @Volatile
        private var cachedSettings: AdminSettingsItem? = null

        fun clearCache() {
            cachedSettings = null
        }
    }
}

private fun defaultAdminSettingsFromEnvironment(): AdminSettingsItem = AdminSettingsItem(
    youtubeRemoteLoginEnabled = envFlag("YOUTUBE_REMOTE_LOGIN_ENABLED"),
)

private fun envFlag(name: String): Boolean = System.getenv(name)?.trim()?.lowercase().let {
    it == "1" || it == "true"
}
