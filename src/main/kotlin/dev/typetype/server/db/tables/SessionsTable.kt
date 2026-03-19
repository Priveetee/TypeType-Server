package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SessionsTable : Table("sessions") {
    val id = text("id")
    val userId = text("user_id")
    val token = text("token").uniqueIndex()
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(id)
}
