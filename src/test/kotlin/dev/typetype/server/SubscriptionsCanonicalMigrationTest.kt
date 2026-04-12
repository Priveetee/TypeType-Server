package dev.typetype.server

import dev.typetype.server.db.DatabaseSubscriptionsCanonicalMigration
import dev.typetype.server.db.tables.SubscriptionsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsCanonicalMigrationTest {
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
    fun `migration canonicalizes and deduplicates subscriptions per user`() {
        transaction {
            SubscriptionsTable.insert {
                it[userId] = TEST_USER_ID
                it[channelUrl] = "https://www.youtube.com/channel/UC123"
                it[name] = "old"
                it[avatarUrl] = "a"
                it[subscribedAt] = 10L
            }
            SubscriptionsTable.insert {
                it[userId] = TEST_USER_ID
                it[channelUrl] = "http://WWW.YouTube.com/channel/UC123/?x=1#f"
                it[name] = "new"
                it[avatarUrl] = "b"
                it[subscribedAt] = 20L
            }
            SubscriptionsTable.insert {
                it[userId] = "other"
                it[channelUrl] = "http://WWW.YouTube.com/channel/UC123/?x=1#f"
                it[name] = "other"
                it[avatarUrl] = "c"
                it[subscribedAt] = 30L
            }
            DatabaseSubscriptionsCanonicalMigration.apply()
            val rows = SubscriptionsTable.selectAll()
                .where { SubscriptionsTable.userId eq TEST_USER_ID }
                .orderBy(SubscriptionsTable.subscribedAt to SortOrder.DESC)
                .toList()
            assertEquals(1, rows.size)
            assertEquals("https://www.youtube.com/channel/UC123", rows.first()[SubscriptionsTable.channelUrl])
            assertEquals("new", rows.first()[SubscriptionsTable.name])
            assertEquals(20L, rows.first()[SubscriptionsTable.subscribedAt])
            val otherRows = SubscriptionsTable.selectAll().where { SubscriptionsTable.userId eq "other" }.toList()
            assertEquals(1, otherRows.size)
            assertEquals("https://www.youtube.com/channel/UC123", otherRows.first()[SubscriptionsTable.channelUrl])
        }
    }
}
