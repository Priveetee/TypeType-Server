package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.WatchLaterItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class WatchLaterService(
    private val eventService: RecommendationEventService? = null,
    private val privacyService: RecommendationPrivacyService = RecommendationPrivacyService(SettingsService()),
) {

    suspend fun getAll(userId: String): List<WatchLaterItem> = DatabaseFactory.query {
        WatchLaterTable.selectAll()
            .where { WatchLaterTable.userId eq userId }
            .orderBy(WatchLaterTable.addedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(userId: String, item: WatchLaterItem): WatchLaterItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            WatchLaterTable.insert {
                it[WatchLaterTable.userId] = userId
                it[url] = item.url
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[duration] = item.duration
                it[addedAt] = now
            }
        }
        if (privacyService.isPersonalizationEnabled(userId)) {
            eventService?.add(
                userId = userId,
                eventType = "watch_later_add",
                videoUrl = item.url,
                uploaderUrl = null,
                title = item.title,
                watchRatio = null,
                watchDurationMs = null,
            )
        }
        return item.copy(addedAt = now)
    }

    suspend fun delete(userId: String, videoUrl: String): Boolean = DatabaseFactory.query {
        WatchLaterTable.deleteWhere { url eq videoUrl and (WatchLaterTable.userId eq userId) } > 0
    }

    private fun ResultRow.toItem() = WatchLaterItem(
        url = this[WatchLaterTable.url],
        title = this[WatchLaterTable.title],
        thumbnail = this[WatchLaterTable.thumbnail],
        duration = this[WatchLaterTable.duration],
        addedAt = this[WatchLaterTable.addedAt],
    )
}
