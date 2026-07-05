package dev.typetype.server.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import java.util.Base64

@Serializable
internal data class CachedSabrSegment(
    val itag: Int,
    val sequence: Int,
    val init: Boolean,
    val startMs: Long,
    val durationMs: Long,
    val mimeType: String,
    val bytesBase64: String,
    val byteLength: Int = -1,
) {
    @Transient
    private var decodedBytes: ByteArray? = null
    val bytes: ByteArray get() = decodedBytes ?: Base64.getDecoder().decode(bytesBase64).also { decodedBytes = it }
    val length: Int get() = byteLength.takeIf { it >= 0 } ?: bytes.size
}

internal fun SabrMediaSegment.toCachedSabrSegment(mimeType: String): CachedSabrSegment = CachedSabrSegment(
    itag = header.itag,
    sequence = header.sequenceNumber,
    init = header.isInitSegment,
    startMs = header.startMs,
    durationMs = header.durationMs,
    mimeType = mimeType,
    bytesBase64 = Base64.getEncoder().encodeToString(data),
    byteLength = length,
)
