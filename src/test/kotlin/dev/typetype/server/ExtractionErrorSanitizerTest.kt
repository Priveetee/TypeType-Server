package dev.typetype.server

import dev.typetype.server.services.ExtractionErrorSanitizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractionErrorSanitizerTest {
    @Test
    fun `normalizes bot prompt with mixed locales`() {
        val raw = "Ngena ngemvume ukuze uqinisekise ukuthi awuyona i-bot"
        assertEquals(
            "This video is only available for members",
            ExtractionErrorSanitizer.sanitize(raw),
        )
    }

    @Test
    fun `keeps normal english error message`() {
        val raw = "This video is only available for members"
        assertEquals(raw, ExtractionErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `normalizes login confirm prompt to members only message`() {
        val raw = "Sign in to confirm you are not a bot"
        assertEquals("This video is only available for members", ExtractionErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `returns null for blank input`() {
        assertEquals(null, ExtractionErrorSanitizer.sanitize(""))
    }
}
