package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamExtractionErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

class StreamExtractionErrorMapperTest {
    @Test
    fun `maps membership restrictions using extractor messages when available`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException("This video is only available for members"))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException("This video is only available for members"))
        assertEquals(ExtractionResult.BadRequest("This video is only available for members", "members_only"), login)
        assertEquals(ExtractionResult.BadRequest("This video is only available for members", "members_only"), paid)
    }

    @Test
    fun `maps membership restrictions to fallback when extractor message is blank`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException(""))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException(""))
        assertEquals(
            ExtractionResult.BadRequest(StreamExtractionErrorMapper.MEMBERS_ONLY_FALLBACK, "members_only"),
            login,
        )
        assertEquals(
            ExtractionResult.BadRequest(StreamExtractionErrorMapper.PAID_CONTENT_FALLBACK, "paid_content"),
            paid,
        )
    }

    @Test
    fun `keeps paid videos distinct from members-only videos`() {
        val result = StreamExtractionErrorMapper.map<Any>(PaidContentException("This video is a paid video"))
        assertEquals(ExtractionResult.BadRequest("This video is a paid video", "paid_content"), result)
    }

    @Test
    fun `maps upcoming premieres to a stable availability code`() {
        val result = StreamExtractionErrorMapper.map<Any>(VideoNotReleaseException("Premieres in 200 days"))
        assertEquals(ExtractionResult.Failure("Premieres in 200 days", "scheduled_premiere"), result)
    }

    @Test
    fun `does not rewrite youtube timeout message to members-only fallback`() {
        val timeout = IllegalStateException("Error occurs when fetching the page. Try increase the loading timeout in Settings.")
        val mapped = StreamExtractionErrorMapper.map<Any>(timeout, sourceUrl = "https://www.youtube.com/watch?v=test")
        assertEquals(ExtractionResult.Failure("Error occurs when fetching the page. Try increase the loading timeout in Settings."), mapped)
    }

    @Test
    fun `maps youtube music premium exception to paid content`() {
        val mapped = StreamExtractionErrorMapper.map<Any>(YoutubeMusicPremiumContentException())
        assertEquals(
            ExtractionResult.BadRequest("This video is a YouTube Music Premium video", "paid_content"),
            mapped,
        )
    }

    @Test
    fun `maps content restrictions to bad request with extractor message`() {
        val result = StreamExtractionErrorMapper.map<Any>(PrivateContentException("private video"))
        assertEquals(ExtractionResult.BadRequest("private video"), result)
    }

    @Test
    fun `maps unknown exceptions to failure`() {
        val result = StreamExtractionErrorMapper.map<Any>(IllegalStateException("boom"))
        assertTrue(result is ExtractionResult.Failure)
        assertEquals("boom", (result as ExtractionResult.Failure).message)
    }
}
