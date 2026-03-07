package dev.typetype.server.db.tables

import org.jetbrains.exposed.sql.Table

object TokenTable : Table("token") {
    val value = text("value")
}
