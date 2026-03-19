package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object AdminSettingsTable : Table("admin_settings") {
    val id = integer("id").default(1)
    val allowRegistration = bool("allow_registration").default(true)
    val allowGuest = bool("allow_guest").default(true)
    val forceEmailVerification = bool("force_email_verification").default(false)
    override val primaryKey = PrimaryKey(id)
}
