package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.StreamExtractionErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException

class StreamExtractionErrorMapperTest {
    @Test
    fun `maps membership restrictions using extractor messages when available`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException("This video is only available for members"))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException("This video is only available for members"))
        assertEquals(ExtractionResult.BadRequest("This video is only available for members"), login)
        assertEquals(ExtractionResult.BadRequest("This video is only available for members"), paid)
    }

    @Test
    fun `maps membership restrictions to fallback when extractor message is blank`() {
        val login = StreamExtractionErrorMapper.map<Any>(NeedLoginException(""))
        val paid = StreamExtractionErrorMapper.map<Any>(PaidContentException(""))
        assertEquals(ExtractionResult.BadRequest(StreamExtractionErrorMapper.MEMBERS_ONLY_FALLBACK), login)
        assertEquals(ExtractionResult.BadRequest(StreamExtractionErrorMapper.MEMBERS_ONLY_FALLBACK), paid)
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
