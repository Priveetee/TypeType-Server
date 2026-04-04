package dev.typetype.server

import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationFeedbackService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SettingsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecommendationFeedbackPrivacyTest {
    private val settings = SettingsService()
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val feedbackService = RecommendationFeedbackService(eventService)

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
    fun `feedback add is no-op when personalization disabled`() = runTest {
        settings.upsert(TEST_USER_ID, SettingsItem(recommendationPersonalizationEnabled = false))
        feedbackService.add(TEST_USER_ID, "not_interested", "https://yt.com/v/a", null)
        assertEquals(0, feedbackService.getAll(TEST_USER_ID).size)
    }
}
