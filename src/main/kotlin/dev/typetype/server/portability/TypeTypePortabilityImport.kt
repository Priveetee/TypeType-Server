package dev.typetype.server.portability

internal object TypeTypePortabilityImport {
    fun write(
        userId: String,
        category: PortabilityCategory,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long = when (category) {
        PortabilityCategory.SUBSCRIPTIONS,
        PortabilityCategory.SUBSCRIPTION_GROUPS,
        PortabilityCategory.HISTORY,
        PortabilityCategory.PLAYLISTS,
        -> TypeTypePortabilityCoreImport.write(userId, category, source, policy)
        PortabilityCategory.WATCH_LATER,
        PortabilityCategory.FAVORITES,
        PortabilityCategory.PROGRESS,
        PortabilityCategory.SEARCH_HISTORY,
        PortabilityCategory.SAVED_PLAYLISTS,
        -> TypeTypePortabilityLibraryImport.write(userId, category, source, policy)
        PortabilityCategory.SETTINGS -> TypeTypePortabilitySettingsImport.write(userId, source)
        PortabilityCategory.CONTENT_FILTERS -> TypeTypePortabilityFilterImport.write(userId, source, policy)
    }
}
