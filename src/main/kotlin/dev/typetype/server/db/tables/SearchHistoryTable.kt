package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object SearchHistoryTable : Table("search_history") {
    val id = text("id")
    val term = text("term")
    val searchedAt = long("searched_at")
    override val primaryKey = PrimaryKey(id)
}
