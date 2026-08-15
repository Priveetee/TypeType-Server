package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

internal object StreamExtractionErrorMapper {
    const val MEMBERS_ONLY_FALLBACK = "This video is only available for members"
    const val PAID_CONTENT_FALLBACK = "This video is a paid video"

    fun <T> map(error: Throwable, sourceUrl: String? = null, fallback: String = "Extraction failed"): ExtractionResult<T> =
        mapByType(error, fallback)

    private fun <T> mapByType(error: Throwable, fallback: String): ExtractionResult<T> = when (error) {
        is NeedLoginException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: MEMBERS_ONLY_FALLBACK,
            "members_only",
        )
        is PaidContentException -> paidContent(error.message)
        is YoutubeMusicPremiumContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: PAID_CONTENT_FALLBACK,
            "paid_content",
        )
        is VideoNotReleaseException -> ExtractionResult.Failure(
            sanitize(error.message) ?: "This premiere has not started yet",
            "scheduled_premiere",
        )
        is AgeRestrictedContentException -> ExtractionResult.BadRequest(
            sanitize(error.message) ?: "This video is age-restricted",
            "age_restricted",
        )
        is GeographicRestrictionException,
        is PrivateContentException -> ExtractionResult.BadRequest(sanitize(error.message) ?: "Content not available")
        else -> ExtractionResult.Failure(
            sanitize(error.message) ?: fallback,
            kind = if (error.isYoutubeSessionRejected()) {
                ExtractionFailureKind.YoutubeSessionRejected
            } else {
                ExtractionFailureKind.Unknown
            },
        )
    }

    private fun paidContent(message: String?): ExtractionResult.BadRequest {
        val sanitized = sanitize(message)
        return if (sanitized == MEMBERS_ONLY_FALLBACK) {
            ExtractionResult.BadRequest(sanitized, "members_only")
        } else {
            ExtractionResult.BadRequest(sanitized ?: PAID_CONTENT_FALLBACK, "paid_content")
        }
    }

    private fun Throwable.isYoutubeSessionRejected(): Boolean =
        generateSequence(this) { it.cause }
            .any { it.javaClass.name == YOUTUBE_SESSION_REJECTED_EXCEPTION }

    private fun sanitize(message: String?): String? = ExtractionErrorSanitizer.sanitize(message)

    private const val YOUTUBE_SESSION_REJECTED_EXCEPTION =
        "org.schabi.newpipe.extractor.exceptions.YoutubeSessionRejectedException"
}
