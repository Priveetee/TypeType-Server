package dev.typetype.server.routes

import dev.typetype.server.models.AudioOnlyStreamResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AudioOnlyMediaTokenService
import dev.typetype.server.services.AudioOnlyStreamKind
import dev.typetype.server.services.AudioOnlyStreamSelection
import dev.typetype.server.services.PublicHlsManifestTokenService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun AudioOnlyStreamSelection.toResponse(
    tokenService: AudioOnlyMediaTokenService,
    hlsTokenService: PublicHlsManifestTokenService?,
    userId: String?,
    url: String,
    preferOriginal: Boolean,
    preferredLocale: String?,
): ExtractionResult<AudioOnlyStreamResponse> {
    val token = tokenService.createToken(userId, url, preferOriginal, preferredLocale, stream.itag, stream.audioTrackId)
    val src = when (kind) {
        AudioOnlyStreamKind.Progressive -> "/streams/audio-only/source?token=${encode(token)}"
        AudioOnlyStreamKind.Hls -> hlsTokenService?.createPath(stream.url)
            ?: return ExtractionResult.Failure("No audio-only stream is available")
    }
    return ExtractionResult.Success(AudioOnlyStreamResponse(
        src = src,
        kind = kind.apiValue,
        mimeType = if (kind == AudioOnlyStreamKind.Hls) HLS_MIME_TYPE else stream.mimeType,
        codec = stream.codec,
        bitrate = stream.bitrate,
        contentLength = stream.contentLength.takeIf { kind == AudioOnlyStreamKind.Progressive && it > 0 },
        duration = response.duration.takeIf { it > 0 },
    ))
}

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
