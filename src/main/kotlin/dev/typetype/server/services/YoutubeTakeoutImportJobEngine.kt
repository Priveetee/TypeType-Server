package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YoutubeTakeoutImportJobEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startPreview(jobId: String, block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }

    fun startCommit(jobId: String, plan: YoutubeTakeoutCommitPlan, block: suspend (YoutubeTakeoutCommitPlan) -> Unit) {
        scope.launch { runCatching { block(plan) } }
    }
}
