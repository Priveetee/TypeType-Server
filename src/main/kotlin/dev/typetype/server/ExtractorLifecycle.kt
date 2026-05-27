package dev.typetype.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.slf4j.LoggerFactory

private const val THROTTLE_CLEANUP_INTERVAL_MS = 3_600_000L

private val log = LoggerFactory.getLogger("ExtractorLifecycle")

internal fun CoroutineScope.launchExtractorLifecycle() {
    launch(Dispatchers.IO) {
        while (true) {
            delay(THROTTLE_CLEANUP_INTERVAL_MS)
            runCatching { YoutubeJavaScriptPlayerManager.clearThrottlingParametersCache() }
                .onFailure { log.info("Throttling cache cleanup failed: ${it.message}") }
        }
    }
}
