package dev.typetype.server.services

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.SubscriptionItem
import java.nio.file.Path
import java.util.zip.ZipFile

object YoutubeTakeoutActivitySignalService {
    private val subscribedRegex = Regex("""(?:${YoutubeTakeoutActivityClassifier.subscribedPattern})\s*<a href=\"([^\"]+)\">([\s\S]*?)</a><br>""", RegexOption.IGNORE_CASE)
    private val likedRegex = Regex("""(?:${YoutubeTakeoutActivityClassifier.likedPattern})\s*<a href=\"([^\"]+)\">([\s\S]*?)</a><br>\s*(?:<a href=\"([^\"]+)\">([\s\S]*?)</a><br>\s*)?([\s\S]*?)<br>""", RegexOption.IGNORE_CASE)
    private val watchUrlRegex = Regex("""https?://(?:www\.)?(?:youtube\.com/(?:watch\?v=|shorts/)|youtu\.be/)[A-Za-z0-9_-]{6,}""")
    private val channelUrlRegex = Regex("""https?://www\.youtube\.com/(?:channel/[A-Za-z0-9_-]+|@[A-Za-z0-9._-]+)""")
    private val spacesRegex = Regex("""\s+""")

    fun parse(zipPath: Path): Pair<List<SubscriptionItem>, List<FavoriteItem>> {
        val subscriptions = mutableListOf<SubscriptionItem>()
        val favorites = mutableListOf<FavoriteItem>()
        ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val normalized = entry.name.lowercase()
                if (entry.isDirectory || !normalized.endsWith(".html") || !normalized.contains("youtube")) return@forEach
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }.replace("\u00a0", " ")
                subscriptions += parseSubscriptions(html)
                favorites += parseFavorites(html)
            }
        }
        return subscriptions.distinctBy { it.channelUrl } to favorites.distinctBy { it.videoUrl }
    }

    private fun parseSubscriptions(html: String): List<SubscriptionItem> {
        return subscribedRegex.findAll(html).mapNotNull { match ->
            val url = channelUrlRegex.find(decode(match.groupValues[1]))?.value ?: return@mapNotNull null
            val name = decode(match.groupValues[2])
            SubscriptionItem(channelUrl = url.replace("http://", "https://"), name = name, avatarUrl = "")
        }.toList()
    }

    private fun parseFavorites(html: String): List<FavoriteItem> {
        return likedRegex.findAll(html).mapNotNull { match ->
            val title = decode(match.groupValues[2])
            if (YoutubeTakeoutUnavailableItem.matches(title)) return@mapNotNull null
            val source = decode(match.groupValues[1]) + " " + title
            val videoUrl = watchUrlRegex.find(source)?.value?.replace("http://", "https://")
                ?: return@mapNotNull null
            FavoriteItem(
                videoUrl = videoUrl,
                favoritedAt = YoutubeTakeoutDateParser.parseEpochMillis(decode(match.groupValues[5])) ?: 0L,
                title = title,
                channelName = decode(match.groupValues[4]),
                channelUrl = decode(match.groupValues[3]).replace("http://", "https://"),
            )
        }.toList()
    }

    private fun decode(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(spacesRegex, " ")
            .trim()
    }
}
