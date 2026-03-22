package dev.typetype.server.services

import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.file.Path
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
                    when {
                        header.any { it.contains("channel", ignoreCase = true) } && header.any { it.contains("title", ignoreCase = true) } -> {
                            if (subscriptionsHeader.isEmpty()) subscriptionsHeader = header
                            subscriptionsRows += rows
                        }
                        header.any { it.contains("playlist", ignoreCase = true) } && header.any { it.contains("description", ignoreCase = true) } -> {
                            if (playlistsHeader.isEmpty()) playlistsHeader = header
                            playlistsRows += rows
                        }
                        header.any { it.contains("playlist", ignoreCase = true) } && header.any { it.contains("video", ignoreCase = true) } -> {
                            if (playlistItemsHeader.isEmpty()) playlistItemsHeader = header
                            playlistItemsRows += rows
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
}
