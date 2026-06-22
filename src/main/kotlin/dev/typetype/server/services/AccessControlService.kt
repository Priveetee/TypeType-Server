package dev.typetype.server.services

class AccessControlService(
    private val settingsService: SettingsService,
    private val allowedChannelsService: AllowedChannelsService,
    private val allowedPlaylistsService: AllowedPlaylistsService,
    private val adminSettingsService: AdminSettingsService,
) {
    suspend fun profileFor(userId: String?): AccessControlProfile {
        val globalAllowList = adminSettingsService.get().accessMode.toAccessMode() == ACCESS_MODE_ALLOW_LIST
        if (globalAllowList) return allowListProfile(userId)
        if (userId == null) return AccessControlProfile.unrestricted
        val userAllowList = settingsService.get(userId).accessMode.toAccessMode() == ACCESS_MODE_ALLOW_LIST
        return if (userAllowList) allowListProfile(userId) else AccessControlProfile.unrestricted
    }

    private suspend fun allowListProfile(userId: String?): AccessControlProfile = AccessControlProfile(
        enabled = true,
        channels = if (userId == null) {
            allowedChannelsService.getGlobalChannels()
        } else {
            allowedChannelsService.getChannels(userId)
        },
        playlists = if (userId == null) {
            allowedPlaylistsService.getGlobalPlaylists()
        } else {
            allowedPlaylistsService.getPlaylists(userId)
        },
    )
}
