package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object ProgressTable : Table("progress") {
    val videoUrl = text("video_url")
    val position = long("position")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(videoUrl)
}
