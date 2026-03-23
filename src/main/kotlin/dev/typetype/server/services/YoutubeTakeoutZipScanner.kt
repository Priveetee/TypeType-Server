package dev.typetype.server.services

import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.file.Path
import java.text.Normalizer
import java.util.zip.ZipFile

object YoutubeTakeoutZipScanner {
    fun scan(zipPath: Path): YoutubeTakeoutZipScanResult {
        val warnings = mutableListOf<String>()
        var subscriptionsHeader = emptyList<String>()
        val subscriptionsRows = mutableListOf<List<String>>()
        var playlistsHeader = emptyList<String>()
        val playlistsRows = mutableListOf<List<String>>()
        var playlistItemsHeader = emptyList<String>()
        val playlistItemsRows = mutableListOf<List<String>>()
        ZipFile(zipPath.toFile()).use { zip ->
            if (zip.size() > YoutubeTakeoutLimits.MAX_ZIP_ENTRIES) error("Archive contains too many files")
            zip.entries().asSequence().filter { it.isDirectory.not() }.forEach { entry ->
                if (entry.size > YoutubeTakeoutLimits.MAX_TMP_BYTES) error("Archive entry too large")
                val name = entry.name.lowercase()
                if (!name.endsWith(".csv")) return@forEach
                zip.getInputStream(entry).use { input ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val (header, rows) = YoutubeTakeoutCsvReader.parse(reader)
                    if (header.isEmpty()) return@use
                    val normalized = header.map { normalize(it) }
                    when {
                        isSubscriptionsHeader(normalized) -> {
                            if (subscriptionsHeader.isEmpty()) subscriptionsHeader = header
                            subscriptionsRows += rows
                        }
                        isPlaylistsHeader(normalized) -> {
                            if (playlistsHeader.isEmpty()) playlistsHeader = header
                            playlistsRows += rows
                        }
                        isPlaylistItemsEntry(name, normalized) -> {
                            val sourceKey = extractPlaylistSourceKey(entry.name)
                            if (sourceKey == null) {
                                if (playlistItemsHeader.isEmpty()) playlistItemsHeader = header
                                playlistItemsRows += rows
                            } else {
                                if (playlistItemsHeader.isEmpty()) playlistItemsHeader = listOf("playlist source key") + header
                                playlistItemsRows += rows.map { row -> listOf(sourceKey) + row }
                            }
                        }
                        else -> warnings += "Unsupported CSV schema: ${entry.name}"
                    }
                }
            }
        }
        return YoutubeTakeoutZipScanResult(
            subscriptionsRows,
            subscriptionsHeader,
            playlistsRows,
            playlistsHeader,
            playlistItemsRows,
            playlistItemsHeader,
            warnings,
        )
    }

    private fun isSubscriptionsHeader(header: List<String>): Boolean =
        header.any { it == "channel id" || it == "id des chaines" } &&
            header.any { it == "channel url" || it == "url des chaines" }

    private fun isPlaylistsHeader(header: List<String>): Boolean =
        header.any { it == "playlist id" || it == "id de la playlist" } &&
            header.any { it.contains("playlist") && (it.contains("title") || it.contains("titre")) }

    private fun isPlaylistItemsEntry(path: String, header: List<String>): Boolean {
        if ("/playlists/" !in path) return false
        if (path.endsWith("/playlists.csv")) return false
        return header.any { it == "video id" || it == "id video" }
    }

    private fun extractPlaylistSourceKey(path: String): String? {
        val fileName = path.substringAfterLast('/').substringBeforeLast('.')
        val normalized = normalize(fileName)
        return when {
            normalized == "watch later" -> "Watch later"
            normalized == "a regarder plus tard" -> "Watch later"
            normalized == "liked videos" -> "Liked videos"
            normalized.startsWith("videos de ") -> fileName.substring(10)
            normalized.startsWith("video de ") -> fileName.substring(9)
            normalized.startsWith("videos from ") -> fileName.substring(12)
            normalized.startsWith("videos que j aime") -> "Liked videos"
            normalized.startsWith("videos liked") -> "Liked videos"
            else -> null
        }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
