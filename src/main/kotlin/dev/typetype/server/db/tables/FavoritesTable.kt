package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object FavoritesTable : Table("favorites") {
    val videoUrl = text("video_url")
    val favoritedAt = long("favorited_at")
    override val primaryKey = PrimaryKey(videoUrl)
}
