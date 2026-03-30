package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException

internal object StreamExtractionErrorMapper {
    const val MEMBERS_ONLY_FALLBACK = "This video is only available for members"

    fun <T> map(error: Throwable, fallback: String = "Extraction failed"): ExtractionResult<T> = when (error) {
        is NeedLoginException,
        is PaidContentException -> ExtractionResult.BadRequest(error.message?.takeIf { it.isNotBlank() } ?: MEMBERS_ONLY_FALLBACK)
        is GeographicRestrictionException,
        is AgeRestrictedContentException,
        is PrivateContentException -> ExtractionResult.BadRequest(error.message ?: "Content not available")
        else -> ExtractionResult.Failure(error.message ?: fallback)
    }
}
