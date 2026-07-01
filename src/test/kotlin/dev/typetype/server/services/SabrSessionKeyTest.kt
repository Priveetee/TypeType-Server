package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SabrSessionKeyTest {
    @Test
    fun `audio track id isolates otherwise identical sabr sessions`() {
        val english = SabrSessionKey(
            videoId = "dqWhXeGQkgU",
            userId = "user-1",
            audioItag = 140,
            audioTrackId = "en.0",
            videoItag = 137,
        )
        val french = english.copy(audioTrackId = "fr.0")

        assertNotEquals(english, french)
    }
}
