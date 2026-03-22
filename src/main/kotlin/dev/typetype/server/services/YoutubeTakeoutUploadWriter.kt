package dev.typetype.server.services

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.file.Files
import java.nio.file.Path

object YoutubeTakeoutUploadWriter {
    fun writeWithLimit(channel: ByteReadChannel, target: Path, maxBytes: Long): Long {
        var written = 0L
        channel.toInputStream().use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    written += read
                    if (written > maxBytes) throw IllegalArgumentException("Takeout archive too large")
                    output.write(buffer, 0, read)
                }
            }
        }
        return written
    }
}
