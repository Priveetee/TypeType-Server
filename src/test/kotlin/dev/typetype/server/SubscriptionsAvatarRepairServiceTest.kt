package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.SubscriptionAvatarRepairer
import dev.typetype.server.services.SubscriptionsService
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionsAvatarRepairServiceTest {
    private val service = SubscriptionsService()

    companion object {
        const val WATCH_CHANNEL_URL = "https://www.youtube.com/channel/UCWatch"
        const val FAVORITE_CHANNEL_URL = "https://www.youtube.com/channel/UCFavorite"
        const val MISSING_CHANNEL_URL = "https://www.youtube.com/channel/UCMissing"
        const val WATCH_AVATAR_URL = "https://avatar.test/watch.jpg"
        const val FAVORITE_AVATAR_URL = "https://avatar.test/favorite.jpg"

        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `getAll repairs avatars from saved collection metadata`() = runTest {
        addSubscription(WATCH_CHANNEL_URL)
        addSubscription(FAVORITE_CHANNEL_URL)
        addSubscription(MISSING_CHANNEL_URL)
        addWatchLater(channelUrl = WATCH_CHANNEL_URL, avatarUrl = WATCH_AVATAR_URL)
        addFavorite(channelUrl = FAVORITE_CHANNEL_URL, avatarUrl = FAVORITE_AVATAR_URL)

        val items = service.getAll(TEST_USER_ID).associateBy { it.channelUrl }

        assertEquals(WATCH_AVATAR_URL, items.getValue(WATCH_CHANNEL_URL).avatarUrl)
        assertEquals(FAVORITE_AVATAR_URL, items.getValue(FAVORITE_CHANNEL_URL).avatarUrl)
        assertEquals("", items.getValue(MISSING_CHANNEL_URL).avatarUrl)
    }

    @Test
    fun `getAll limits avatar repairs per call`() = runTest {
        repeat(26) { index ->
            val channelUrl = "https://www.youtube.com/channel/UCBound$index"
            addSubscription(channelUrl)
            addHistory(channelUrl = channelUrl, avatarUrl = "https://avatar.test/$index.jpg", watchedAt = index.toLong())
        }

        val first = service.getAll(TEST_USER_ID)
        val second = service.getAll(TEST_USER_ID)

        assertEquals(25, first.count { it.avatarUrl.isNotBlank() })
        assertEquals(26, second.count { it.avatarUrl.isNotBlank() })
    }

    @Test
    fun `avatar repair scans past unrepairable empty subscriptions`() = runTest {
        addWatchLater(channelUrl = WATCH_CHANNEL_URL, avatarUrl = WATCH_AVATAR_URL)
        val items = (0 until 25).map { index ->
            SubscriptionItem(channelUrl = "https://www.youtube.com/channel/UCEmpty$index", name = "Channel", avatarUrl = "")
        } + SubscriptionItem(channelUrl = WATCH_CHANNEL_URL, name = "Channel", avatarUrl = "")

        val repaired = DatabaseFactory.query { SubscriptionAvatarRepairer.repair(userId = TEST_USER_ID, items = items) }

        assertEquals(WATCH_AVATAR_URL, repaired.last().avatarUrl)
    }

    private suspend fun addSubscription(channelUrl: String): Unit {
        service.add(TEST_USER_ID, SubscriptionItem(channelUrl = channelUrl, name = "Channel", avatarUrl = ""))
    }

    private suspend fun addWatchLater(channelUrl: String, avatarUrl: String): Unit = DatabaseFactory.query {
        WatchLaterTable.insert {
            it[userId] = TEST_USER_ID; it[url] = "https://video.test/watch"; it[title] = "Video"; it[thumbnail] = ""
            it[duration] = 1L; it[addedAt] = 1L; it[channelName] = "Channel"; it[WatchLaterTable.channelUrl] = channelUrl
            it[channelAvatar] = avatarUrl
        }
    }

    private suspend fun addFavorite(channelUrl: String, avatarUrl: String): Unit = DatabaseFactory.query {
        FavoritesTable.insert {
            it[userId] = TEST_USER_ID; it[videoUrl] = "https://video.test/favorite"; it[favoritedAt] = 1L
            it[channelName] = "Channel"; it[FavoritesTable.channelUrl] = channelUrl; it[channelAvatar] = avatarUrl
        }
    }

    private suspend fun addHistory(channelUrl: String, avatarUrl: String, watchedAt: Long): Unit = DatabaseFactory.query {
        HistoryTable.insert {
            it[id] = "history-$watchedAt"; it[userId] = TEST_USER_ID; it[url] = "https://video.test/$watchedAt"
            it[title] = "Video"; it[thumbnail] = ""; it[channelName] = "Channel"; it[HistoryTable.channelUrl] = channelUrl
            it[channelAvatar] = avatarUrl; it[duration] = 1L; it[progress] = 0L; it[HistoryTable.watchedAt] = watchedAt
        }
    }

}
