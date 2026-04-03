package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import dev.typetype.server.services.RecommendationInterestService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecommendationInterestServiceTest {
    private val service = RecommendationInterestService()

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
    fun `update upserts and accumulates channel and topics`() {
        runSuspend {
            service.update(
                userId = TEST_USER_ID,
                eventType = "watch",
                uploaderUrl = "https://yt.com/c/a",
                title = "linux kernel scheduler",
                watchRatio = 0.8,
            )
            service.update(
                userId = TEST_USER_ID,
                eventType = "watch",
                uploaderUrl = "https://yt.com/c/a",
                title = "linux kernel scheduler",
                watchRatio = 0.8,
            )
        }

        val channelScore = runSuspendWithResult {
            DatabaseFactory.query {
                UserChannelInterestTable.selectAll()
                    .where { (UserChannelInterestTable.userId eq TEST_USER_ID) and (UserChannelInterestTable.uploaderUrl eq "https://yt.com/c/a") }
                    .single()[UserChannelInterestTable.score]
            }
        }
        assertEquals(6.0, channelScore)

        val topicScore = runSuspendWithResult {
            DatabaseFactory.query {
                UserTopicInterestTable.selectAll()
                    .where { (UserTopicInterestTable.userId eq TEST_USER_ID) and (UserTopicInterestTable.topic eq "linux") }
                    .single()[UserTopicInterestTable.score]
            }
        }
        assertEquals(6.0, topicScore)
    }

    private fun runSuspend(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }

    private fun <T> runSuspendWithResult(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
