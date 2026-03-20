package dev.typetype.server.services

import dev.typetype.server.models.RestorePipePipeResultItem
import java.nio.file.Files
import java.nio.file.Path

class PipePipeBackupImporterService(
    private val sqliteReader: PipePipeBackupSqliteReader = PipePipeBackupSqliteReader(),
    private val persister: PipePipeBackupPersisterService = PipePipeBackupPersisterService(),
) {

    suspend fun restore(userId: String, backupZipPath: Path): RestorePipePipeResultItem {
        val sqlitePath = PipePipeBackupZipExtractor.extractDatabase(backupZipPath)
        return try {
            val snapshot = sqliteReader.read(sqlitePath)
            persister.persist(userId, snapshot)
        } finally {
            Files.deleteIfExists(sqlitePath)
            Files.deleteIfExists(backupZipPath)
        }
    }
}
