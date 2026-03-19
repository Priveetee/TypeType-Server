package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object WatchLaterTable : Table("watch_later") {
    val userId = text("user_id")
    val url = text("url")
    val title = text("title")
    val thumbnail = text("thumbnail")
    val duration = long("duration")
    val addedAt = long("added_at")
    override val primaryKey = PrimaryKey(userId, url)
}
