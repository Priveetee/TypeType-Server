package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PortabilityArchiveInspectorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `inventory reports safe archive entries`() {
        val archive = zip("exportInfo" to "{}", "stores/subscriptions" to "[]")
        val inventory = requireNotNull(PortabilityArchiveInspector.inspect(archive))
        assertEquals(setOf("exportInfo", "stores/subscriptions"), inventory.names)
        assertEquals(4L, inventory.expandedBytes)
    }

    @Test
    fun `inventory rejects traversal entry names`() {
        val archive = zip("../outside" to "invalid")
        assertThrows(IllegalArgumentException::class.java) {
            PortabilityArchiveInspector.inspect(archive)
        }
    }

    private fun zip(vararg entries: Pair<String, String>): Path {
        val path = Files.createTempFile(directory, "archive-", ".zip")
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value.toByteArray())
                output.closeEntry()
            }
        }
        return path
    }
}
