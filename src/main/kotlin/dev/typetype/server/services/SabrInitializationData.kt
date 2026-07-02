package dev.typetype.server.services

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat

internal object SabrInitializationData {
    fun ingest(format: YoutubeSabrFormat, holder: SabrSessionHolder): Boolean {
        val data = fetch(format) ?: return false
        return holder.session.streamState.ingestInitializationData(format, data)
    }

    private fun fetch(format: YoutubeSabrFormat): ByteArray? {
        val url = format.initializationUrl?.takeUnless { it.isBlank() } ?: return null
        val start = format.initRangeStart
        val end = format.initRangeEnd
        if (start < 0L || end < start) return null
        val headers = mapOf("Range" to listOf("bytes=$start-$end"))
        return runCatching { NewPipe.getDownloader().get(url, headers) }
            .getOrNull()
            ?.takeIf { it.responseCode() == 200 || it.responseCode() == 206 }
            ?.rawResponseBody()
            ?.takeIf { it.isNotEmpty() }
    }
}
