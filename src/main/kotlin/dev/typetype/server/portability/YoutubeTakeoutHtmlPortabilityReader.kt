package dev.typetype.server.portability

import dev.typetype.server.services.YoutubeTakeoutActivitySignalService
import dev.typetype.server.services.YoutubeTakeoutHistoryParser
import java.io.Reader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object YoutubeTakeoutHtmlPortabilityReader {
    fun read(zip: ZipFile, entries: List<ZipEntry>, sink: PortabilityRecordSink) {
        entries.asSequence().filter(::isYoutubeHtml).forEach { entry ->
            zip.getInputStream(entry).bufferedReader().use { reader ->
                readWindows(reader) { html -> writeWindow(html, sink) }
            }
        }
    }

    private fun writeWindow(html: String, sink: PortabilityRecordSink) {
        YoutubeTakeoutHistoryParser.parse(html).forEach { sink.write(it.toPortability()) }
        val (subscriptions, favorites) = YoutubeTakeoutActivitySignalService.parseHtml(html)
        subscriptions.forEach { sink.write(it.toPortability()) }
        favorites.forEach { sink.write(it.toPortability()) }
    }

    private fun readWindows(reader: Reader, block: (String) -> Unit) {
        val buffer = CharArray(READ_CHARS)
        val window = StringBuilder(WINDOW_CHARS + READ_CHARS)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            window.append(buffer, 0, read)
            if (window.length >= WINDOW_CHARS) {
                block(window.toString())
                window.delete(0, window.length - OVERLAP_CHARS)
            }
        }
        if (window.isNotEmpty()) block(window.toString())
    }

    private fun isYoutubeHtml(entry: ZipEntry): Boolean =
        entry.name.endsWith(".html", ignoreCase = true) && "youtube" in entry.name.lowercase()

    private const val READ_CHARS = 32 * 1024
    private const val WINDOW_CHARS = 512 * 1024
    private const val OVERLAP_CHARS = 128 * 1024
}
