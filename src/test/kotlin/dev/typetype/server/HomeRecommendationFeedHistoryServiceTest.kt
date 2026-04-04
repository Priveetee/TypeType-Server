package dev.typetype.server

import dev.typetype.server.services.RecommendationFeedHistoryService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationFeedHistoryServiceTest {
    private val service = RecommendationFeedHistoryService()

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
    fun `recordShown increments show count`() = runTest {
        service.recordShown(TEST_USER_ID, listOf("https://yt.com/v/a"))
        service.recordShown(TEST_USER_ID, listOf("https://yt.com/v/a"))
        val loaded = service.load(TEST_USER_ID)
        assertEquals(2, loaded["https://yt.com/v/a"]?.showCount)
    }

    @Test
    fun `recordShown stores unique urls only once per call`() = runTest {
        service.recordShown(TEST_USER_ID, listOf("https://yt.com/v/a", "https://yt.com/v/a"))
        val loaded = service.load(TEST_USER_ID)
        assertTrue("https://yt.com/v/a" in loaded.keys)
        assertEquals(1, loaded["https://yt.com/v/a"]?.showCount)
    }
}
