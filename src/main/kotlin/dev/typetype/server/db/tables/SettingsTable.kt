package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object SettingsTable : Table("settings") {
    val id = integer("id").default(1)
    val defaultService = integer("default_service").default(0)
    val defaultQuality = text("default_quality").default("1080p")
    val autoplay = bool("autoplay").default(true)
    override val primaryKey = PrimaryKey(id)
}
