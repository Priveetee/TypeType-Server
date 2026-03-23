package dev.typetype.server.services

import dev.typetype.server.models.SubscriptionItem
import java.nio.file.Path
import java.util.zip.ZipFile

object YoutubeTakeoutActivitySignalService {
    private val subscribedRegex = Regex("""(?:Vous vous êtes abonné à|You subscribed to)\s*<a href=\"([^\"]+)\">([\s\S]*?)</a><br>""")
    private val likedRegex = Regex("""(?:Vous avez aimé|You liked)\s*<a href=\"([^\"]+)\">([\s\S]*?)</a><br>""")
    private val watchUrlRegex = Regex("""https?://www\.youtube\.com/watch\?v=[A-Za-z0-9_-]{6,}""")
    private val channelUrlRegex = Regex("""https?://www\.youtube\.com/(?:channel/[A-Za-z0-9_-]+|@[A-Za-z0-9._-]+)""")
    private val spacesRegex = Regex("""\s+""")

    fun parse(zipPath: Path): Pair<List<SubscriptionItem>, List<String>> {
        val subscriptions = mutableListOf<SubscriptionItem>()
        val favorites = mutableListOf<String>()
        ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val normalized = entry.name.lowercase()
                if (entry.isDirectory || !normalized.endsWith(".html") || !normalized.contains("youtube")) return@forEach
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }.replace("\u00a0", " ")
                subscriptions += parseSubscriptions(html)
                favorites += parseFavorites(html)
            }
        }
        return subscriptions.distinctBy { it.channelUrl } to favorites.distinct()
    }

    private fun parseSubscriptions(html: String): List<SubscriptionItem> {
        return subscribedRegex.findAll(html).mapNotNull { match ->
            val url = channelUrlRegex.find(decode(match.groupValues[1]))?.value ?: return@mapNotNull null
            val name = decode(match.groupValues[2])
            SubscriptionItem(channelUrl = url.replace("http://", "https://"), name = name, avatarUrl = "")
        }.toList()
    }

    private fun parseFavorites(html: String): List<String> {
        return likedRegex.findAll(html).mapNotNull { match ->
            val source = decode(match.groupValues[1]) + " " + decode(match.groupValues[2])
            watchUrlRegex.find(source)?.value?.replace("http://", "https://")
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
