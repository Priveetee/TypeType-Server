package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

internal object StreamExtractionErrorMapper {
    const val MEMBERS_ONLY_FALLBACK = "This video is only available for members"
    private const val EXTRACTOR_TIMEOUT_MESSAGE = "Error occurs when fetching the page. Try increase the loading timeout in Settings."

    fun <T> map(error: Throwable, sourceUrl: String? = null, fallback: String = "Extraction failed"): ExtractionResult<T> = when {
        error is NeedLoginException ||
            error is PaidContentException ||
            error is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(error.message?.takeIf { it.isNotBlank() } ?: MEMBERS_ONLY_FALLBACK)
        sourceUrl?.contains("youtube.com/watch", ignoreCase = true) == true &&
            error.message == EXTRACTOR_TIMEOUT_MESSAGE -> ExtractionResult.BadRequest(MEMBERS_ONLY_FALLBACK)
        else -> mapByType(error, fallback)
    }

    private fun <T> mapByType(error: Throwable, fallback: String): ExtractionResult<T> = when (error) {
        is NeedLoginException,
        is PaidContentException,
        is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(error.message?.takeIf { it.isNotBlank() } ?: MEMBERS_ONLY_FALLBACK)
        is GeographicRestrictionException,
        is AgeRestrictedContentException,
        is PrivateContentException -> ExtractionResult.BadRequest(error.message ?: "Content not available")
        else -> ExtractionResult.Failure(error.message ?: fallback)
    }
}
