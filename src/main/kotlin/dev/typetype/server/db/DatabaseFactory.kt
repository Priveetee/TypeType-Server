package dev.typetype.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.TokenTable
import dev.typetype.server.db.tables.WatchLaterTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {

    fun init(url: String, user: String, password: String) {
        val dbPassword = password
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.create(
                TokenTable,
                HistoryTable,
                SubscriptionsTable,
                PlaylistsTable,
                PlaylistVideosTable,
                WatchLaterTable,
                ProgressTable,
                FavoritesTable,
                SettingsTable,
                SearchHistoryTable,
                BlockedChannelsTable,
                BlockedVideosTable,
            )
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS name TEXT")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS thumbnail_url TEXT")
        }
    }

    suspend fun <T> query(block: () -> T): T =
        withContext(Dispatchers.IO) { transaction { block() } }
}
