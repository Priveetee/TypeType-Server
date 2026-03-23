package dev.typetype.server.services

object YoutubeTakeoutFactory {
    fun create(subscriptionsService: SubscriptionsService, playlistService: PlaylistService): YoutubeTakeoutImportJobService {
        return YoutubeTakeoutImportJobService(
            parser = YoutubeTakeoutParserService(),
            previewService = YoutubeTakeoutPreviewService(subscriptionsService, playlistService),
            importerService = YoutubeTakeoutImporterService(subscriptionsService, playlistService, YoutubeTakeoutPlaylistKeyService()),
            store = YoutubeTakeoutImportJobStore(),
            statusStore = YoutubeTakeoutImportJobStatusStore(),
            archiveStore = YoutubeTakeoutImportJobArchiveStore(),
            reportStore = YoutubeTakeoutImportJobReportStore(),
            flagsStore = YoutubeTakeoutImportJobFlagsStore(),
            cache = YoutubeTakeoutImportCache(),
        )
    }
}
