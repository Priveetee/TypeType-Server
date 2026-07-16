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
        assertEquals(listOf("https://www.youtube.com/watch?v=like789"), parsed.favorites.map { it.videoUrl })
        assertEquals(1_758_045_443_000L, parsed.favorites.first().favoritedAt)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse detects subscriptions without channel url`() {
        val zip = Files.createTempFile("yt-takeout-subscriptions-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv"))
            out.write("channel id,channel title\nUC1,Linus Tech Tips\n".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals("https://www.youtube.com/channel/UC1", parsed.subscriptions.first().channelUrl)
        assertEquals("Linus Tech Tips", parsed.subscriptions.first().name)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse detects localized french takeout csv files`() {
        val zip = Files.createTempFile("yt-takeout-french-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/abonnements/abonnements.csv"))
            out.write("ID des chaînes,URL des chaînes,Titres des chaînes\nUC1,https://www.youtube.com/channel/UC1,Channel\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/playlists.csv"))
            out.write("ID de la playlist,Titre (d'origine) de la playlist\nPL1,a\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Vidéos de a.csv"))
            out.write("ID vidéo,Code temporel de création de la vidéo de la playlist\nabc123,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Vidéos de Watch later.csv"))
            out.write("ID vidéo,Code temporel de création de la vidéo de la playlist\nwatch456,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals(1, parsed.playlists.size)
        assertEquals(1, parsed.playlistItems["a"]?.size)
        assertEquals("YouTube video abc123", parsed.playlistItems["a"]?.first()?.title)
        assertEquals(1_767_225_600_000L, parsed.playlistItems["a"]?.first()?.addedAt)
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", parsed.playlistItems["a"]?.first()?.thumbnail)
        assertEquals(1, parsed.watchLater.size)
        Files.deleteIfExists(zip)
    }

    @Test
    fun `parse preserves takeout playlist order and added dates`() {
        val zip = Files.createTempFile("yt-takeout-playlist-order-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/playlists.csv"))
            out.write("ID de la playlist,Titre (d'origine) de la playlist\nPL123456,Imported\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube et YouTube Music/playlists/Imported.csv"))
            out.write(
                """
                ID vidéo,Code temporel de création de la vidéo de la playlist
                newer000001,2026-01-02T00:00:00+00:00
                older000001,2026-01-01T00:00:00+00:00
                """.trimIndent().plus("\n").toByteArray(),
            )
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)
        val imported = parsed.playlistItems.values.single()

        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=newer000001",
                "https://www.youtube.com/watch?v=older000001",
            ),
            imported.map { it.url },
        )
        assertEquals(listOf(1_767_312_000_000L, 1_767_225_600_000L), imported.map { it.addedAt })
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
