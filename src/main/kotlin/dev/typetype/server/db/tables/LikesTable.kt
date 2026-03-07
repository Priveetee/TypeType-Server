package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object LikesTable : Table("likes") {
    val videoUrl = text("video_url")
    val likedAt = long("liked_at")
    override val primaryKey = PrimaryKey(videoUrl)
}
