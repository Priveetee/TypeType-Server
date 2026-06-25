package dev.typetype.server.services

object YoutubeTakeoutFactory {
    fun create(
        subscriptionsService: SubscriptionsService,
        playlistService: PlaylistService,
        historyService: HistoryService,
        favoritesService: FavoritesService,
        watchLaterService: WatchLaterService,
        streamService: StreamService? = null,
    ): YoutubeTakeoutImportJobService {
        val previewLookup = YoutubeTakeoutPreviewLookupService(historyService, favoritesService, watchLaterService)
        val signalImport = YoutubeTakeoutSignalImportService(favoritesService, watchLaterService, historyService)
        val metadataResolver = streamService?.let(::VideoMetadataResolver)
        return YoutubeTakeoutImportJobService(
            parser = YoutubeTakeoutParserService(),
            previewService = YoutubeTakeoutPreviewService(subscriptionsService, playlistService, previewLookup),
            importerService = YoutubeTakeoutImporterService(subscriptionsService, playlistService, signalImport, YoutubeTakeoutPlaylistKeyService(), metadataResolver),
            store = YoutubeTakeoutImportJobStore(),
            statusStore = YoutubeTakeoutImportJobStatusStore(),
            archiveStore = YoutubeTakeoutImportJobArchiveStore(),
            reportStore = YoutubeTakeoutImportJobReportStore(),
            flagsStore = YoutubeTakeoutImportJobFlagsStore(),
            cache = YoutubeTakeoutImportCache(),
        )
    }
}
