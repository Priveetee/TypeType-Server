package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutImportJobStatus
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import dev.typetype.server.models.YoutubeTakeoutCommitRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path

class YoutubeTakeoutImportJobService(
    private val parser: YoutubeTakeoutParserService,
    private val previewService: YoutubeTakeoutPreviewService,
    private val importerService: YoutubeTakeoutImporterService,
    private val store: YoutubeTakeoutImportJobStore = YoutubeTakeoutImportJobStore(),
    private val statusStore: YoutubeTakeoutImportJobStatusStore = YoutubeTakeoutImportJobStatusStore(),
    private val archiveStore: YoutubeTakeoutImportJobArchiveStore = YoutubeTakeoutImportJobArchiveStore(),
    private val reportStore: YoutubeTakeoutImportJobReportStore = YoutubeTakeoutImportJobReportStore(),
    private val previewStore: YoutubeTakeoutImportJobPreviewStore = YoutubeTakeoutImportJobPreviewStore(),
    private val flagsStore: YoutubeTakeoutImportJobFlagsStore = YoutubeTakeoutImportJobFlagsStore(),
    private val runtimeCache: YoutubeTakeoutJobRuntimeCache = YoutubeTakeoutJobRuntimeCache(),
    private val jobEngine: YoutubeTakeoutImportJobEngine = YoutubeTakeoutImportJobEngine(),
    private val privacyService: YoutubeTakeoutPrivacyService = YoutubeTakeoutPrivacyService(),
    private val cache: YoutubeTakeoutImportCache = YoutubeTakeoutImportCache(),
) {
    suspend fun create(userId: String, archivePath: Path): YoutubeTakeoutImportJobStatus {
        val jobId = store.create(userId, archivePath)
        return statusStore.getStatus(userId, jobId) ?: error("Failed to create job")
    }

    suspend fun get(userId: String, jobId: String): YoutubeTakeoutImportJobStatus? = statusStore.getStatus(userId, jobId)

    suspend fun preview(userId: String, jobId: String): YoutubeTakeoutPreviewItem {
        cache.getPreview(jobId)?.let { return it }
        previewStore.getPreview(userId, jobId)?.let { persisted ->
            val preview = Json.decodeFromString(YoutubeTakeoutPreviewItem.serializer(), persisted)
            cache.setPreview(jobId, preview)
            return preview
        }
        statusStore.updateStatus(jobId, "pending", "queued_preview", 0)
        jobEngine.startPreview(jobId) {
            statusStore.updateStatus(jobId, "running", "parsing", 10)
            val archive = archiveStore.getArchivePath(userId, jobId)
            val parsed = parser.parse(Path.of(archive))
            cache.setParsed(jobId, parsed)
            val preview = previewService.build(userId, parsed)
            cache.setPreview(jobId, preview)
            previewStore.persistPreview(jobId, Json.encodeToString(preview))
            flagsStore.setParseCompleted(jobId)
            statusStore.updateStatus(jobId, "completed", "preview_ready", 100)
        }
        return YoutubeTakeoutPreviewItem(
            counts = dev.typetype.server.models.YoutubeTakeoutCategoryCounts(0, 0, 0),
            dedup = dev.typetype.server.models.YoutubeTakeoutCategoryCounts(0, 0, 0),
            samples = dev.typetype.server.models.YoutubeTakeoutPreviewSamples(),
            warnings = listOf("Preview started"),
            errors = emptyList(),
        )
    }

    suspend fun commit(userId: String, jobId: String, request: YoutubeTakeoutCommitRequest?): YoutubeTakeoutImportJobStatus {
        val flags = flagsStore.getFlags(userId, jobId)
        if (flags.importCompleted) return statusStore.getStatus(userId, jobId) ?: error("Missing job")
        if (!flags.parseCompleted) preview(userId, jobId)
        val plan = YoutubeTakeoutCommitPlanner.fromRequest(request)
        runtimeCache.setPlan(jobId, plan)
        flagsStore.setImportStarted(jobId)
        statusStore.updateStatus(jobId, "pending", "queued_commit", 70)
        jobEngine.startCommit(jobId, plan) { commitPlan ->
            statusStore.updateStatus(jobId, "running", "importing", 75)
            val parsed = cache.getParsed(jobId) ?: error("Missing parsed cache")
            val report = importerService.commit(userId, parsed, commitPlan)
            reportStore.persistReport(jobId, Json.encodeToString(report))
            flagsStore.setImportCompleted(jobId)
            statusStore.updateStatus(jobId, "completed", "completed", 100)
            privacyService.deleteArchive(archiveStore.getArchivePath(userId, jobId))
        }
        return waitForCompletion(userId, jobId)
    }

    private suspend fun waitForCompletion(userId: String, jobId: String): YoutubeTakeoutImportJobStatus {
        repeat(50) {
            val status = statusStore.getStatus(userId, jobId) ?: error("Missing job")
            if (status.phase == "completed") return status
            Thread.sleep(100)
        }
        return statusStore.getStatus(userId, jobId) ?: error("Missing job")
    }

    suspend fun report(userId: String, jobId: String): YoutubeTakeoutImportReportItem? {
        val reportJson = reportStore.getReport(userId, jobId) ?: return null
        return Json.decodeFromString(YoutubeTakeoutImportReportItem.serializer(), reportJson)
    }

    suspend fun purgeExpired() = YoutubeTakeoutImportCleanupService(privacyService).purgeExpiredJobs()
}
