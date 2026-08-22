package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.StreamService
import dev.typetype.server.testStreamResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPlaybackAccessValidatorTest {
    @Test
    fun `uses linked YouTube session even when public metadata is accessible`() = runBlocking {
        val authenticated = ExtractionResult.Success(testStreamResponse().copy(title = "Authenticated"))
        val validator = validator(
            publicResult = ExtractionResult.Success(testStreamResponse().copy(title = "Public")),
            authenticatedResult = authenticated,
        )

        assertEquals(authenticated, validator.resolve("user-id", "video-id"))
    }

    @Test
    fun `uses linked YouTube session for age-restricted playback`() = runBlocking {
        val authenticated = ExtractionResult.Success(testStreamResponse())
        val validator = validator(
            publicResult = ExtractionResult.BadRequest("Confirm your age", "age_restricted"),
            authenticatedResult = authenticated,
        )

        assertEquals(authenticated, validator.resolve("user-id", "video-id"))
    }

    @Test
    fun `asks for YouTube connection when restricted playback has no session`() = runBlocking {
        val validator = validator(
            publicResult = ExtractionResult.BadRequest("Sign in", "members_only"),
            authenticatedResult = null,
        )

        assertEquals(
            ExtractionResult.BadRequest("Connect YouTube to access this video", "youtube_session_required"),
            validator.resolve(null, "video-id"),
        )
    }

    @Test
    fun `keeps membership error when linked account lacks access`() = runBlocking {
        val membersOnly = ExtractionResult.BadRequest("Join this channel", "members_only")
        val validator = validator(
            publicResult = ExtractionResult.Success(testStreamResponse().copy(requiresMembership = true)),
            authenticatedResult = membersOnly,
        )

        assertEquals(membersOnly, validator.resolve("user-id", "video-id"))
    }

    private fun validator(
        publicResult: ExtractionResult<StreamResponse>,
        authenticatedResult: ExtractionResult<StreamResponse>?,
    ): SabrPlaybackAccessValidator = SabrPlaybackAccessValidator(
        publicStreamService = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> = publicResult
        },
        youtubeSessionStreamInfo = { _, _ -> authenticatedResult },
    )
}
