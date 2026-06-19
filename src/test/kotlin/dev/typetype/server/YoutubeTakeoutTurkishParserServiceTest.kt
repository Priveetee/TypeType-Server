package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutParserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutTurkishParserServiceTest {
    @Test
    fun `parse detects turkish takeout csv files`() {
        val zip = Files.createTempFile("yt-takeout-turkish-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/YouTube ve YouTube Music/abonelikler/abonelikler.csv"))
            out.write("Kanal Kimliği,Kanal URL'si,Kanal başlığı\nUC1,https://www.youtube.com/channel/UC1,Kanal\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube ve YouTube Music/oynatma listeleri/oynatma listeleri.csv"))
            out.write("Oynatma listesi kimliği,Oynatma listesi başlığı\nPL1,Deneme\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube ve YouTube Music/oynatma listeleri/Deneme videoları.csv"))
            out.write("Video kimliği,Oynatma listesi videosu oluşturma zaman damgası\nabc123,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("Takeout/YouTube ve YouTube Music/oynatma listeleri/Daha sonra izle.csv"))
            out.write("Video kimliği,Oynatma listesi videosu oluşturma zaman damgası\nwatch456,2026-01-01T00:00:00+00:00\n".toByteArray())
            out.closeEntry()
        }

        val parsed = YoutubeTakeoutParserService().parse(zip)

        assertEquals(1, parsed.subscriptions.size)
        assertEquals(1, parsed.playlists.size)
        assertEquals(1, parsed.playlistItems["Deneme"]?.size)
        assertEquals(1, parsed.watchLater.size)
        Files.deleteIfExists(zip)
    }
}
