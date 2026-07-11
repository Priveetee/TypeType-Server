package dev.typetype.server

import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.normalized
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsDeArrowOptionsTest {
    @Test
    fun `keeps supported read only dearrow preferences`() {
        val settings = SettingsItem(
            deArrowTitleMode = "original",
            deArrowThumbnailMode = "random",
            deArrowTrustMode = "locked",
        ).normalized()

        assertEquals("original", settings.deArrowTitleMode)
        assertEquals("random", settings.deArrowThumbnailMode)
        assertEquals("locked", settings.deArrowTrustMode)
    }

    @Test
    fun `normalizes unsupported dearrow preferences`() {
        val settings = SettingsItem(
            deArrowTitleMode = "submit",
            deArrowThumbnailMode = "vote",
            deArrowTrustMode = "everything",
        ).normalized()

        assertEquals("dearrow", settings.deArrowTitleMode)
        assertEquals("dearrow_or_random", settings.deArrowThumbnailMode)
        assertEquals("accepted", settings.deArrowTrustMode)
    }
}
