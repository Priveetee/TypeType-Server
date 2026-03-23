package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutActivitySignalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YoutubeTakeoutActivitySignalServiceTest {
    @Test
    fun `parse extracts subscriptions and likes from activity html`() {
        val zip = createZip()
        val result = YoutubeTakeoutActivitySignalService.parse(zip)
        assertEquals(1, result.first.size)
        assertTrue(result.first.first().channelUrl.contains("youtube.com/channel/UC999"))
        assertEquals(listOf("https://www.youtube.com/watch?v=like123"), result.second)
        Files.deleteIfExists(zip)
    }

    private fun createZip(): Path {
        val zip = Files.createTempFile("yt-activity-signals-", ".zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("Takeout/Mon activité/YouTube/MonActivité.html"))
            out.write("""
                <html><body>
                Vous vous êtes abonné à <a href="https://www.youtube.com/channel/UC999">Channel Name</a><br>
                Vous avez aimé <a href="https://www.youtube.com/watch?v=like123">A video</a><br>
                </body></html>
            """.trimIndent().toByteArray())
            out.closeEntry()
        }
        return zip
    }
}
