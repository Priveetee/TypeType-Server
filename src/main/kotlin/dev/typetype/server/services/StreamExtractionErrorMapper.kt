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

    fun <T> map(error: Throwable, sourceUrl: String? = null, fallback: String = "Extraction failed"): ExtractionResult<T> = when {
        error is NeedLoginException ||
            error is PaidContentException ||
            error is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(sanitize(error.message) ?: MEMBERS_ONLY_FALLBACK)
        else -> mapByType(error, fallback)
    }

    private fun <T> mapByType(error: Throwable, fallback: String): ExtractionResult<T> = when (error) {
        is NeedLoginException,
        is PaidContentException,
        is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(sanitize(error.message) ?: MEMBERS_ONLY_FALLBACK)
        is GeographicRestrictionException,
        is AgeRestrictedContentException,
        is PrivateContentException -> ExtractionResult.BadRequest(sanitize(error.message) ?: "Content not available")
        else -> ExtractionResult.Failure(sanitize(error.message) ?: fallback)
    }

    private fun sanitize(message: String?): String? = ExtractionErrorSanitizer.sanitize(message)
}
