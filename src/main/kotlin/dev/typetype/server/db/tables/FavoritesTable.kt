package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object FavoritesTable : Table("favorites") {
    val userId = text("user_id")
    val videoUrl = text("video_url")
    val favoritedAt = long("favorited_at")
    override val primaryKey = PrimaryKey(userId, videoUrl)
}
