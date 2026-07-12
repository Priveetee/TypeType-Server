package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers

internal fun Headers.audioOnlyRangeHeader(contentLength: Long?): String? {
    val range = get(HttpHeaders.Range) ?: return null
    val openStart = Regex("^bytes=(\\d+)-$").matchEntire(range.trim())?.groupValues?.get(1)?.toLongOrNull()
    if (openStart == null) return range
    return if (contentLength != null && contentLength in 1..COMPLETE_AUDIO_ONLY_BYTES && openStart < contentLength) {
        "bytes=$openStart-${contentLength - 1L}"
    } else {
        initialAudioRange(openStart)
    }
}

internal fun audioOnlyProbeRangeHeader(): String = initialAudioRange(0)

internal fun ExtractionResult<ProxyResponse>.ensureProgressiveAudio(): ExtractionResult<ProxyResponse> =
    when (this) {
        is ExtractionResult.Success -> {
            if (data.contentType.contains("mpegurl", ignoreCase = true)) {
                data.close()
                ExtractionResult.Failure("Audio-only source did not return progressive audio")
            } else {
                this
            }
        }
        is ExtractionResult.BadRequest -> this
        is ExtractionResult.Failure -> this
    }

private fun initialAudioRange(start: Long): String =
    "bytes=$start-${start + INITIAL_AUDIO_ONLY_BYTES - 1}"

private const val INITIAL_AUDIO_ONLY_BYTES = 1024 * 1024
private const val COMPLETE_AUDIO_ONLY_BYTES = 4 * 1024 * 1024
