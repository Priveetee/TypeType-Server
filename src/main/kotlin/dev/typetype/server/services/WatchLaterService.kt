package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.WatchLaterItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class WatchLaterService {

    suspend fun getAll(): List<WatchLaterItem> = DatabaseFactory.query {
        WatchLaterTable.selectAll()
            .orderBy(WatchLaterTable.addedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(item: WatchLaterItem): WatchLaterItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            WatchLaterTable.insert {
                it[url] = item.url
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[duration] = item.duration
                it[addedAt] = now
            }
        }
        return item.copy(addedAt = now)
    }

    suspend fun delete(videoUrl: String): Boolean = DatabaseFactory.query {
        WatchLaterTable.deleteWhere { url eq videoUrl } > 0
    }

    private fun ResultRow.toItem() = WatchLaterItem(
        url = this[WatchLaterTable.url],
        title = this[WatchLaterTable.title],
        thumbnail = this[WatchLaterTable.thumbnail],
        duration = this[WatchLaterTable.duration],
        addedAt = this[WatchLaterTable.addedAt],
    )
}
