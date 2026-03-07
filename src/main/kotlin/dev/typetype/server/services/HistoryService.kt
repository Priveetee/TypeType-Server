package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class HistoryService {

    suspend fun getAll(): List<HistoryItem> = DatabaseFactory.query {
        HistoryTable.selectAll()
            .orderBy(HistoryTable.watchedAt to SortOrder.DESC)
            .map { it.toHistoryItem() }
    }

    suspend fun add(item: HistoryItem): HistoryItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            HistoryTable.insert {
                it[HistoryTable.id] = id
                it[url] = item.url
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[channelName] = item.channelName
                it[channelUrl] = item.channelUrl
                it[duration] = item.duration
                it[progress] = item.progress
                it[watchedAt] = now
            }
        }
        return item.copy(id = id, watchedAt = now)
    }

    suspend fun delete(id: String): Boolean = DatabaseFactory.query {
        HistoryTable.deleteWhere { HistoryTable.id eq id } > 0
    }

    suspend fun deleteAll(): Unit = DatabaseFactory.query {
        HistoryTable.deleteAll()
    }

    private fun ResultRow.toHistoryItem() = HistoryItem(
        id = this[HistoryTable.id],
        url = this[HistoryTable.url],
        title = this[HistoryTable.title],
        thumbnail = this[HistoryTable.thumbnail],
        channelName = this[HistoryTable.channelName],
        channelUrl = this[HistoryTable.channelUrl],
        duration = this[HistoryTable.duration],
        progress = this[HistoryTable.progress],
        watchedAt = this[HistoryTable.watchedAt],
    )
}
