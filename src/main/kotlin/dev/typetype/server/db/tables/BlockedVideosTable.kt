package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object BlockedVideosTable : Table("blocked_videos") {
    val videoUrl = text("video_url")
    val blockedAt = long("blocked_at")
    override val primaryKey = PrimaryKey(videoUrl)
}
