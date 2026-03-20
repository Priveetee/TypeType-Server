package dev.typetype.server.services

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object PipePipeBackupZipExtractor {
    fun extractDatabase(backupZipPath: Path): Path {
        val extractedDb = Files.createTempFile("pipepipe-db-", ".sqlite")
        ZipFile(backupZipPath.toFile()).use { zip ->
            val dbEntry = zip.entries().asSequence().firstOrNull { entry ->
                val name = entry.name.substringAfterLast('/').lowercase()
                !entry.isDirectory && (name == "newpipe.db" || name == "pipepipe.db")
            } ?: throw IllegalArgumentException("Missing backup database file")
            zip.getInputStream(dbEntry).use { input ->
                Files.newOutputStream(extractedDb).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return extractedDb
    }
}
