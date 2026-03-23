package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutParserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutParserServiceTest {
    @Test
    fun `parse detects history watch later and liked videos`() {
        val zip = createZip()
        val parsed = YoutubeTakeoutParserService().parse(zip)
        assertEquals(1, parsed.history.size)
        assertTrue(parsed.history.first().url.contains("watch?v=abc123"))
        assertEquals(1, parsed.watchLater.size)
        assertTrue(parsed.watchLater.first().url.contains("watch?v=watch456"))
        assertEquals(listOf("https://www.youtube.com/watch?v=like789"), parsed.favorites)
        Files.deleteIfExists(zip)
    }

    private fun createZip(): Path {
        val zip = Files.createTempFile("yt-takeout-parser-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/Mon activité/YouTube/MonActivité.html"))
            out.write("""
                <html><body>
                You watched <a href="https://www.youtube.com/watch?v=abc123">Title</a><br>
                <a href="https://www.youtube.com/channel/UC1">Channel</a><br>
                22 Mar 2026, 19:27:08 CET<br>
                </body></html>
            """.trimIndent().toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Watch later.csv"))
            out.write("video id,added at\nwatch456,2025-09-16T17:57:23+00:00\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube/playlists/Liked videos.csv"))
            out.write("video id,added at\nlike789,2025-09-16T17:57:23+00:00\n".toByteArray())
            out.closeEntry()
        }
        return zip
    }
}
