package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

class AudioOnlyStreamResolver(
    private val streamService: StreamService,
    private val youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
) {
    suspend fun resolve(
        url: String,
        userId: String?,
        preferOriginal: Boolean,
        preferredLocale: String?,
        allowHls: Boolean = true,
    ): ExtractionResult<AudioOnlyStreamSelection> {
        val sessionResult = if (userId != null && youtubeSessionStreamInfo != null) {
            youtubeSessionStreamInfo.invoke(userId, url)
        } else {
            null
        }
        val publicResult = if (sessionResult.hasAudioStreams()) null else streamService.getStreamInfo(url)
        return when (val result = publicResult.resolveWith(sessionResult)) {
            is ExtractionResult.Success -> select(result.data, preferOriginal, preferredLocale, allowHls)
            is ExtractionResult.BadRequest -> result
            is ExtractionResult.Failure -> result
        }
    }

    private fun select(
        response: StreamResponse,
        preferOriginal: Boolean,
        preferredLocale: String?,
        allowHls: Boolean,
    ): ExtractionResult<AudioOnlyStreamSelection> {
        val progressive = AudioOnlyStreamSelector.selectProgressive(response, preferOriginal, preferredLocale)
        if (progressive != null) {
            return ExtractionResult.Success(AudioOnlyStreamSelection(response, progressive, AudioOnlyStreamKind.Progressive))
        }
        val hls = (if (allowHls) AudioOnlyStreamSelector.selectHls(response, preferOriginal, preferredLocale) else null)
            ?: return ExtractionResult.Failure("No audio-only stream is available")
        return ExtractionResult.Success(AudioOnlyStreamSelection(response, hls, AudioOnlyStreamKind.Hls))
    }

    private fun ExtractionResult<StreamResponse>?.resolveWith(
        sessionResult: ExtractionResult<StreamResponse>?,
    ): ExtractionResult<StreamResponse> {
        if (this == null && sessionResult != null) return sessionResult
        if (this == null) return ExtractionResult.Failure("Stream extraction failed")
        if (sessionResult == null) return this
        if (this is ExtractionResult.Success && sessionResult is ExtractionResult.Success) {
            return ExtractionResult.Success(sessionResult.data.mergeWithPublic(data))
        }
        if (this is ExtractionResult.Success) return this
        return sessionResult
    }

    private fun ExtractionResult<StreamResponse>?.hasAudioStreams(): Boolean =
        this is ExtractionResult.Success && data.audioStreams.isNotEmpty()

    private fun StreamResponse.mergeWithPublic(public: StreamResponse): StreamResponse =
        copy(audioStreams = audioStreams.ifEmpty { public.audioStreams })
}
