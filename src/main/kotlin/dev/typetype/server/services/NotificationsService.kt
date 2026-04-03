package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.models.MarkNotificationsReadResponse
import dev.typetype.server.models.NotificationItem
import dev.typetype.server.models.NotificationsResponse
import dev.typetype.server.models.UnreadCountResponse
import dev.typetype.server.models.VideoItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class NotificationsService(
    private val subscriptionFeedService: SubscriptionFeedService,
) {
    suspend fun getNotifications(userId: String, page: Int, limit: Int): NotificationsResponse {
        val items = buildItems(userId)
        val unreadCount = unreadCount(items, userId)
        val from = page * limit
        if (from >= items.size) return NotificationsResponse(items = emptyList(), unreadCount = unreadCount, nextpage = null)
        val to = minOf(from + limit, items.size)
        val nextpage = if (to < items.size) (page + 1).toString() else null
        return NotificationsResponse(items = items.subList(from, to), unreadCount = unreadCount, nextpage = nextpage)
    }

    suspend fun getUnreadCount(userId: String): UnreadCountResponse {
        val items = buildItems(userId)
        return UnreadCountResponse(unreadCount = unreadCount(items, userId))
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

    private suspend fun unreadCount(items: List<NotificationItem>, userId: String): Int {
        val lastSeenUploaded = getLastSeenUploaded(userId)
        return items.count { it.createdAt > lastSeenUploaded }
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
}
