package dev.typetype.server

import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SettingsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecommendationEventPrivacyTest {
    private val settings = SettingsService()
    private val service = RecommendationEventService(RecommendationInterestService())

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `event add is no-op when personalization disabled`() = kotlinx.coroutines.test.runTest {
        settings.upsert(TEST_USER_ID, SettingsItem(recommendationPersonalizationEnabled = false))
        service.add(
            userId = TEST_USER_ID,
            eventType = "click",
            videoUrl = "https://yt.com/v/a",
            uploaderUrl = "https://yt.com/c/a",
            title = "hello world",
            watchRatio = null,
            watchDurationMs = null,
        )
        assertEquals(0, service.getAll(TEST_USER_ID).size)
    }
}
