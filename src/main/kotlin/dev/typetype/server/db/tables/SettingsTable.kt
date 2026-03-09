package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SettingsTable : Table("settings") {
    val id = integer("id").default(1)
    val defaultService = integer("default_service").default(0)
    val defaultQuality = text("default_quality").default("1080p")
    val autoplay = bool("autoplay").default(true)
    val volume = double("volume").default(1.0)
    val muted = bool("muted").default(false)
    override val primaryKey = PrimaryKey(id)
}
