package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object BlockedChannelsTable : Table("blocked_channels") {
    val channelUrl = text("channel_url")
    val blockedAt = long("blocked_at")
    override val primaryKey = PrimaryKey(channelUrl)
}
