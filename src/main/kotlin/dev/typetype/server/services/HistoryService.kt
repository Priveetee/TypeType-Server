package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.v1.core.LowerCase
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class HistoryService {
    suspend fun search(userId: String, q: String?, from: Long?, to: Long?, limit: Int, offset: Int): Pair<List<HistoryItem>, Long> = DatabaseFactory.query {
        val query = HistoryTable.selectAll().where { HistoryTable.userId eq userId }
        if (!q.isNullOrBlank()) {
            val pattern = "%${q.lowercase()}%"
            query.andWhere { (LowerCase(HistoryTable.title) like pattern) or (LowerCase(HistoryTable.channelName) like pattern) }
        }
        if (from != null) query.andWhere { HistoryTable.watchedAt greaterEq from }
        if (to != null) query.andWhere { HistoryTable.watchedAt less to }
        val total = query.count()
        val rows = query.orderBy(HistoryTable.watchedAt to SortOrder.DESC, HistoryTable.id to SortOrder.DESC).limit(limit).offset(offset.toLong()).toList()
        val items = HistoryProgressMapper.toHistoryItems(userId, rows)
        items to total
    }

    suspend fun add(userId: String, item: HistoryItem): HistoryItem = insert(userId, item, System.currentTimeMillis())

    suspend fun addImported(userId: String, item: HistoryItem): HistoryItem = insert(userId, item, item.watchedAt.takeIf { it > 0 } ?: System.currentTimeMillis())

    suspend fun addImportedBatch(userId: String, items: List<HistoryItem>): Int {
        if (items.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val rows = items.map { item ->
            val watchedAt = item.watchedAt.takeIf { it > 0 } ?: now
            Triple(UUID.randomUUID().toString(), item, watchedAt)
        }
        DatabaseFactory.query {
            val progressByUrl = HistoryProgressMapper.savedProgressSeconds(userId, rows.map { it.second.url })
            HistoryTable.batchInsert(data = rows, shouldReturnGeneratedValues = false) { (id, item, watchedAt) ->
                this[HistoryTable.id] = id
                this[HistoryTable.userId] = userId
                this[HistoryTable.url] = item.url
                this[HistoryTable.title] = item.title
                this[HistoryTable.thumbnail] = item.thumbnail
                this[HistoryTable.channelName] = item.channelName
                this[HistoryTable.channelUrl] = item.channelUrl
                this[HistoryTable.channelAvatar] = item.channelAvatar
                this[HistoryTable.duration] = item.duration
                this[HistoryTable.progress] = maxOf(item.progress, progressByUrl[item.url] ?: 0L)
                this[HistoryTable.watchedAt] = watchedAt
            }
        }
        return rows.size
    }

    suspend fun dedupKeys(userId: String): Set<Pair<String, Long>> = DatabaseFactory.query { HistoryTable.selectAll().where { HistoryTable.userId eq userId }.map { it[HistoryTable.url] to it[HistoryTable.watchedAt] }.toSet() }

    suspend fun delete(userId: String, id: String): Boolean = DatabaseFactory.query { HistoryTable.deleteWhere { HistoryTable.id eq id and (HistoryTable.userId eq userId) } > 0 }

    suspend fun deleteAll(userId: String): Unit = DatabaseFactory.query { HistoryTable.deleteWhere { HistoryTable.userId eq userId } }

    private suspend fun insert(userId: String, item: HistoryItem, watchedAt: Long): HistoryItem {
        val id = UUID.randomUUID().toString()
        val progress = DatabaseFactory.query { maxOf(item.progress, HistoryProgressMapper.savedProgressSeconds(userId, item.url) ?: 0L) }
        DatabaseFactory.query {
            HistoryTable.insert {
                it[HistoryTable.id] = id
                it[HistoryTable.userId] = userId
                it[url] = item.url
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[channelName] = item.channelName
                it[channelUrl] = item.channelUrl
                it[channelAvatar] = item.channelAvatar
                it[duration] = item.duration
                it[HistoryTable.progress] = progress
                it[HistoryTable.watchedAt] = watchedAt
            }
        }
        return item.copy(id = id, progress = progress, watchedAt = watchedAt)
    }
}
