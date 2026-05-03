package dev.typetype.server.db.tables

import dev.typetype.server.DEFAULT_INSTANCE_NAME
import org.jetbrains.exposed.v1.core.Table

object AdminSettingsTable : Table("admin_settings") {
    val id = integer("id").default(1)
    val name = text("name").default(DEFAULT_INSTANCE_NAME)
    val tagline = text("tagline").nullable()
    val logoUrl = text("logo_url").nullable()
    val bannerUrl = text("banner_url").nullable()
    val minAndroidClientVersion = text("min_android_client_version").nullable()
    val allowRegistration = bool("allow_registration").default(true)
    val allowGuest = bool("allow_guest").default(true)
    val forceEmailVerification = bool("force_email_verification").default(false)
    override val primaryKey = PrimaryKey(id)
}
