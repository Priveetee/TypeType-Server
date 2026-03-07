package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object PlaylistVideosTable : Table("playlist_videos") {
    val id = text("id")
    val playlistId = text("playlist_id").references(PlaylistsTable.id, onDelete = ReferenceOption.CASCADE)
    val url = text("url")
    val title = text("title")
    val thumbnail = text("thumbnail")
    val duration = long("duration")
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
}
