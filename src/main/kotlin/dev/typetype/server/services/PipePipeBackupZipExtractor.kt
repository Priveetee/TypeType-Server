package dev.typetype.server.services

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object PipePipeBackupZipExtractor {
    fun extractDatabase(backupZipPath: Path): Path {
        val extractedDb = Files.createTempFile("pipepipe-db-", ".sqlite")
        ZipFile(backupZipPath.toFile()).use { zip ->
            val dbEntry = zip.getEntry("newpipe.db")
                ?: throw IllegalArgumentException("Missing newpipe.db in backup")
            zip.getInputStream(dbEntry).use { input ->
                Files.newOutputStream(extractedDb).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return extractedDb
    }
}
