package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter streams takeout categories and keeps playlist order`() {
        val archive = directory.resolve("takeout.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.entry(
                "Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv",
                "Channel Id,Channel Url,Channel Title\nUC123456789012,https://youtube.com/channel/UC123456789012,Channel\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/playlists.csv",
                "Playlist ID,Playlist Title\nPL123456789,Imported\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Videos de Imported.csv",
                "Video ID,Video Title,Video Added Timestamp\nvideo000001,First,2026-01-02T00:00:00Z\nvideo000002,Second,2026-01-01T00:00:00Z\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Watch later.csv",
                "Video ID,Video Title\nwatch000001,Later\n",
            )
            output.entry(
                "Takeout/YouTube and YouTube Music/playlists/Liked videos.csv",
                "Video ID,Video Title\nliked000001,Liked\n",
            )
            output.entry(
                "Takeout/My Activity/YouTube/watch-history.html",
                "You watched <a href=\"https://www.youtube.com/watch?v=seen000001\">Seen</a><br>1 Jan 2026, 12:00:00 CET<br>",
            )
        }
        val input = PortabilityInputFactory.create(archive, "takeout.zip", "application/zip")
        val spool = PortabilitySpool.create(directory)
        val adapter = YoutubeTakeoutPortabilityAdapter()

        assertEquals(PortabilityFormat.YOUTUBE_TAKEOUT, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(3L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.WATCH_LATER])
        assertEquals(1L, spool.counts()[PortabilityCategory.FAVORITES])
        val positions = mutableListOf<Int>()
        spool.forEachChild(PortabilityCategory.PLAYLISTS, "PL123456789") { record ->
            positions += (record as PortabilityPlaylistVideo).position
        }
        assertEquals(listOf(0, 1), positions)
        assertTrue(spool.issues().isEmpty())
        spool.delete()
    }

    private fun ZipOutputStream.entry(name: String, value: String) {
        putNextEntry(ZipEntry(name))
        write(value.toByteArray())
        closeEntry()
    }
}
