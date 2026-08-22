package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.SubscriptionGroupMembershipResult
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TypeTypeBackupCategory
import dev.typetype.server.services.TypeTypeBackupService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionBackupConsistencyTest {
    private val subscriptions = SubscriptionsService()
    private val groups = SubscriptionGroupsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `backup stays restorable when a subscription is added between section reads`() = runTest {
        val group = (groups.create(SOURCE, "New") as SubscriptionGroupWriteResult.Success).group
        val capturedSubscriptions = mockk<SubscriptionsService>()
        coEvery { capturedSubscriptions.getAll(SOURCE, any()) } coAnswers {
            subscriptions.add(SOURCE, SubscriptionItem(CHANNEL_URL, "Channel", ""))
            assertEquals(
                SubscriptionGroupMembershipResult.Success,
                groups.addSubscription(SOURCE, group.id, CHANNEL_URL),
            )
            emptyList()
        }
        val service = backupService(capturedSubscriptions)

        val backup = service.export(SOURCE, setOf(TypeTypeBackupCategory.SUBSCRIPTIONS))
        val restored = service.restore(TARGET, backup)

        assertEquals(emptyList<SubscriptionItem>(), backup.subscriptions)
        assertEquals(emptyList<String>(), backup.subscriptionGroups?.single()?.channelUrls)
        assertEquals(1, restored.restored["subscriptionGroups"])
        assertEquals(0, restored.restored["subscriptionGroupMemberships"])
    }

    private fun backupService(subscriptions: SubscriptionsService) = TypeTypeBackupService(
        subscriptions = subscriptions,
        history = mockk(),
        playlists = mockk(),
        watchLater = mockk(),
        favorites = mockk(),
        progress = mockk(),
        searchHistory = mockk(),
        savedPlaylists = mockk(),
        settings = mockk(),
        blocked = mockk(),
        allowedChannels = mockk(),
        allowedPlaylists = mockk(),
    )
}

private const val SOURCE = "concurrent-backup-source"
private const val TARGET = "concurrent-backup-target"
private const val CHANNEL_URL = "https://youtube.com/channel/concurrent"
