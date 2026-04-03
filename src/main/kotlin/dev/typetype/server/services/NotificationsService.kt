package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.models.MarkNotificationsReadResponse
import dev.typetype.server.models.NotificationItem
import dev.typetype.server.models.NotificationsResponse
import dev.typetype.server.models.UnreadCountResponse
import dev.typetype.server.models.VideoItem
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class NotificationsService(
    private val subscriptionFeedService: SubscriptionFeedService,
) {
    private val unreadCache = ConcurrentHashMap<String, CachedUnread>()

    suspend fun getNotifications(userId: String, page: Int, limit: Int): NotificationsResponse {
        val items = buildItems(userId)
        val unreadCount = unreadCount(items, userId, refresh = true)
        val from = page * limit
        if (from >= items.size) return NotificationsResponse(items = emptyList(), unreadCount = unreadCount, nextpage = null)
        val to = minOf(from + limit, items.size)
        val nextpage = if (to < items.size) (page + 1).toString() else null
        return NotificationsResponse(items = items.subList(from, to), unreadCount = unreadCount, nextpage = nextpage)
    }

    suspend fun getUnreadCount(userId: String): UnreadCountResponse {
        val now = System.currentTimeMillis()
        unreadCache[userId]?.takeIf { it.expiresAt > now }?.let { return UnreadCountResponse(unreadCount = it.value) }
        val items = buildItems(userId)
        return UnreadCountResponse(unreadCount = unreadCount(items, userId, refresh = true))
    }

    suspend fun markAllRead(userId: String): MarkNotificationsReadResponse {
        val now = System.currentTimeMillis()
        val latestUploaded = buildItems(userId).firstOrNull()?.createdAt ?: 0L
        DatabaseFactory.query {
            val updated = NotificationStatesTable.update({ NotificationStatesTable.userId eq userId }) {
                it[subscriptionLastSeenUploaded] = latestUploaded
                it[updatedAt] = now
            }
            if (updated == 0) {
                NotificationStatesTable.insert {
                    it[NotificationStatesTable.userId] = userId
                    it[subscriptionLastSeenUploaded] = latestUploaded
                    it[updatedAt] = now
                }
            }
        }
        unreadCache[userId] = CachedUnread(value = 0, expiresAt = now + UNREAD_CACHE_TTL_MS)
        return MarkNotificationsReadResponse(readAt = now, unreadCount = 0)
    }

    private suspend fun buildItems(userId: String): List<NotificationItem> = subscriptionFeedService.getAll(userId)
        .asSequence()
        .filter { it.uploaded > 0L }
        .groupBy { notificationKey(it) }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.uploaded } }
        .sortedByDescending { it.uploaded }
        .map { it.toNotificationItem() }
        .toList()

    private suspend fun getLastSeenUploaded(userId: String): Long = DatabaseFactory.query {
        NotificationStatesTable.selectAll().where { NotificationStatesTable.userId eq userId }
            .singleOrNull()?.get(NotificationStatesTable.subscriptionLastSeenUploaded) ?: 0L
    }

    private suspend fun unreadCount(items: List<NotificationItem>, userId: String, refresh: Boolean): Int {
        if (!refresh) {
            val now = System.currentTimeMillis()
            unreadCache[userId]?.takeIf { it.expiresAt > now }?.let { return it.value }
        }
        val lastSeenUploaded = getLastSeenUploaded(userId)
        val value = items.count { it.createdAt > lastSeenUploaded }
        val now = System.currentTimeMillis()
        unreadCache[userId] = CachedUnread(value = value, expiresAt = now + UNREAD_CACHE_TTL_MS)
        return value
    }

    private fun notificationKey(video: VideoItem): String =
        video.uploaderUrl.ifBlank { video.uploaderName.ifBlank { video.url } }

    private fun VideoItem.toNotificationItem(): NotificationItem = NotificationItem(
        type = "subscription_new_video",
        title = "$uploaderName uploaded a new video",
        createdAt = uploaded,
        channelUrl = uploaderUrl,
        channelName = uploaderName,
        channelAvatarUrl = uploaderAvatarUrl,
        video = this,
    )

    private data class CachedUnread(
        val value: Int,
        val expiresAt: Long,
    )

    private companion object {
        const val UNREAD_CACHE_TTL_MS = 30_000L
    }
}
