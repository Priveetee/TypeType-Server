package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutParsedData
import dev.typetype.server.models.YoutubeTakeoutPreviewItem

class YoutubeTakeoutImportCache {
    private val previewCache = mutableMapOf<String, YoutubeTakeoutPreviewItem>()
    private val parsedCache = mutableMapOf<String, YoutubeTakeoutParsedData>()

    fun getPreview(jobId: String): YoutubeTakeoutPreviewItem? = previewCache[jobId]

    fun setPreview(jobId: String, preview: YoutubeTakeoutPreviewItem) {
        previewCache[jobId] = preview
    }

    fun getParsed(jobId: String): YoutubeTakeoutParsedData? = parsedCache[jobId]

    fun setParsed(jobId: String, parsed: YoutubeTakeoutParsedData) {
        parsedCache[jobId] = parsed
    }
}
