package dev.typetype.server

import dev.typetype.server.services.PipePipeBackupSqliteReader
import dev.typetype.server.services.PipePipeBackupZipExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PipePipeBackupSqliteReaderTest {

    @Test
    fun `reader parses expected snapshot`() {
        val zip = PipePipeBackupTestFixtures.createBackupZip()
        val sqlite = PipePipeBackupZipExtractor.extractDatabase(zip)
        val snapshot = PipePipeBackupSqliteReader().read(sqlite)
        Files.deleteIfExists(sqlite)
        Files.deleteIfExists(zip)
        assertEquals(1, snapshot.subscriptions.size)
        assertEquals(1, snapshot.history.size)
        assertEquals(1, snapshot.playlists.size)
        assertEquals(1, snapshot.progress.size)
        assertEquals(1, snapshot.searchHistory.size)
        assertEquals("https://youtube.com/watch?v=1", snapshot.history.first().url)
    }
}
