package dev.typetype.server

import dev.typetype.server.services.RecommendationEventService
import dev.typetype.server.services.RecommendationInterestService
import dev.typetype.server.services.SubscriptionShortsSignalService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionShortsSignalServiceTest {
    private val eventService = RecommendationEventService(RecommendationInterestService())
    private val service = SubscriptionShortsSignalService(eventService)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `instant skips penalize more than late skips`() = runTest {
        val urlInstant = "https://www.youtube.com/shorts/instant"
        val urlLate = "https://www.youtube.com/shorts/late"
        eventService.add(TEST_USER_ID, "short_skip", urlInstant, null, null, null, 200)
        eventService.add(TEST_USER_ID, "short_skip", urlLate, null, null, null, 8_000)
        val penalties = service.load(TEST_USER_ID)
        assertTrue((penalties[urlInstant] ?: 1.0) < (penalties[urlLate] ?: 1.0))
    }
}
