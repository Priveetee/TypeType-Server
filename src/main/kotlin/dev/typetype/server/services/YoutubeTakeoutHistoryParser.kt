package dev.typetype.server.services

import dev.typetype.server.models.HistoryItem

object YoutubeTakeoutHistoryParser {
    private val rowRegex = Regex("""(?:${YoutubeTakeoutActivityClassifier.watchedPattern})\s*<a href=\"([^\"]+)\">([\s\S]*?)</a><br>\s*(?:<a href=\"([^\"]+)\">([\s\S]*?)</a><br>\s*)?([\s\S]*?)<br>""", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("""https?://(?:www\.)?(?:youtube\.com/(?:watch\?v=|shorts/)|youtu\.be/)[A-Za-z0-9_-]{6,}""")
    private val tagRegex = Regex("<[^>]+>")
    private val spacesRegex = Regex("\\s+")

    fun parse(html: String): List<HistoryItem> {
        val resolvedHtml = html.replace("\u00a0", " ")
        return rowRegex.findAll(resolvedHtml).mapNotNull { match ->
            val url = extractUrl(match.groupValues[1]) ?: return@mapNotNull null
            val title = decode(match.groupValues[2])
            val channelUrl = match.groupValues[3].takeIf { it.isNotBlank() }.orEmpty()
            val channelName = decode(match.groupValues[4]).ifBlank { "Unknown channel" }
            val watchedAt = parseDate(decode(match.groupValues[5]))
            HistoryItem(
                url = url,
                title = title,
                thumbnail = "",
                channelName = channelName,
                channelUrl = channelUrl,
                channelAvatar = "",
                duration = 0,
                progress = 0,
                watchedAt = watchedAt,
            )
        }.toList().distinctBy { it.url to it.watchedAt }
    }

    private fun parseDate(value: String): Long {
        return YoutubeTakeoutDateParser.parseEpochMillis(value) ?: 0L
    }

    private fun extractUrl(value: String): String? {
        val decoded = decode(value)
        val direct = urlRegex.find(decoded)?.value
        if (direct != null) return direct
        val fromText = urlRegex.find(decode(tagRegex.replace(decoded, " ")))?.value
        return fromText
    }

    private fun decode(value: String): String = tagRegex.replace(value, " ")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(spacesRegex, " ")
        .trim()
}
