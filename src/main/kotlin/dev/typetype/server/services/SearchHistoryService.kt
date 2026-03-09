package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.models.SearchHistoryItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class SearchHistoryService {

    suspend fun getAll(): List<SearchHistoryItem> = DatabaseFactory.query {
        SearchHistoryTable.selectAll()
            .orderBy(SearchHistoryTable.searchedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(term: String): SearchHistoryItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            SearchHistoryTable.insert {
                it[SearchHistoryTable.id] = id
                it[SearchHistoryTable.term] = term
                it[searchedAt] = now
            }
        }
        return SearchHistoryItem(id = id, term = term, searchedAt = now)
    }

    suspend fun deleteAll(): Unit = DatabaseFactory.query {
        SearchHistoryTable.deleteAll()
    }

    private fun ResultRow.toItem() = SearchHistoryItem(
        id = this[SearchHistoryTable.id],
        term = this[SearchHistoryTable.term],
        searchedAt = this[SearchHistoryTable.searchedAt],
    )
}
