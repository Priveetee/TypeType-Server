package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutImportJobStatus
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class YoutubeTakeoutImportJobService(
    private val parser: YoutubeTakeoutParserService,
    private val previewService: YoutubeTakeoutPreviewService,
    private val importerService: YoutubeTakeoutImporterService,
    private val store: YoutubeTakeoutImportJobStore = YoutubeTakeoutImportJobStore(),
    private val statusStore: YoutubeTakeoutImportJobStatusStore = YoutubeTakeoutImportJobStatusStore(),
    private val archiveStore: YoutubeTakeoutImportJobArchiveStore = YoutubeTakeoutImportJobArchiveStore(),
    private val reportStore: YoutubeTakeoutImportJobReportStore = YoutubeTakeoutImportJobReportStore(),
    private val flagsStore: YoutubeTakeoutImportJobFlagsStore = YoutubeTakeoutImportJobFlagsStore(),
    private val cache: YoutubeTakeoutImportCache = YoutubeTakeoutImportCache(),
) {
    suspend fun create(userId: String, archivePath: Path): YoutubeTakeoutImportJobStatus {
        val jobId = store.create(userId, archivePath)
        return statusStore.getStatus(userId, jobId) ?: error("Failed to create job")
    }

    suspend fun get(userId: String, jobId: String): YoutubeTakeoutImportJobStatus? = statusStore.getStatus(userId, jobId)

    suspend fun preview(userId: String, jobId: String): YoutubeTakeoutPreviewItem {
        cache.getPreview(jobId)?.let { return it }
        statusStore.updateStatus(jobId, "running", "parsing", 10)
        val archive = archiveStore.getArchivePath(userId, jobId)
        val parsed = parser.parse(Path.of(archive))
        cache.setParsed(jobId, parsed)
        val preview = previewService.build(userId, parsed)
        cache.setPreview(jobId, preview)
        flagsStore.setParseCompleted(jobId)
        statusStore.updateStatus(jobId, "completed", "preview_ready", 100)
        return preview
    }

    suspend fun commit(userId: String, jobId: String): YoutubeTakeoutImportJobStatus {
        val flags = flagsStore.getFlags(userId, jobId)
        if (flags.importCompleted) return statusStore.getStatus(userId, jobId) ?: error("Missing job")
        if (!flags.parseCompleted) preview(userId, jobId)
        flagsStore.setImportStarted(jobId)
        statusStore.updateStatus(jobId, "running", "importing", 70)
        val parsed = cache.getParsed(jobId) ?: error("Missing parsed cache")
        val report = importerService.commit(userId, parsed)
        reportStore.persistReport(jobId, Json.encodeToString(report))
        flagsStore.setImportCompleted(jobId)
        statusStore.updateStatus(jobId, "completed", "completed", 100)
        cleanupArchive(archiveStore.getArchivePath(userId, jobId))
        return statusStore.getStatus(userId, jobId) ?: error("Missing job")
    }

    suspend fun report(userId: String, jobId: String): YoutubeTakeoutImportReportItem? {
        val reportJson = reportStore.getReport(userId, jobId) ?: return null
        return Json.decodeFromString(YoutubeTakeoutImportReportItem.serializer(), reportJson)
    }

    private fun cleanupArchive(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { Files.deleteIfExists(Path.of(path)) }
    }
}
