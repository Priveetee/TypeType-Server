package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object TokenTable : Table("token") {
    val value = text("value")
}
