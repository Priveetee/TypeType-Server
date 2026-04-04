package dev.typetype.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.db.tables.RecommendationEventsTable
import dev.typetype.server.db.tables.RecommendationFeedbackTable
import dev.typetype.server.db.tables.RecommendationFeedHistoryTable
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
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
            minimumIdle = 2
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.create(
                UsersTable,
                SessionsTable,
                AdminSettingsTable,
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
                PasswordResetTable,
                RecommendationFeedbackTable,
                RecommendationEventsTable,
                RecommendationFeedHistoryTable,
                UserChannelInterestTable,
                UserTopicInterestTable,
                YoutubeTakeoutImportJobsTable,
                YoutubeTakeoutPlaylistKeysTable,
                BugReportsTable,
                NotificationStatesTable,
            )
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS name TEXT")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS thumbnail_url TEXT")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS subtitles_enabled BOOLEAN NOT NULL DEFAULT false")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_subtitle_language TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS default_audio_language TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS prefer_original_language BOOLEAN NOT NULL DEFAULT false")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS recommendation_personalization_enabled BOOLEAN NOT NULL DEFAULT true")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS subscription_sync_interval INTEGER NOT NULL DEFAULT 0")
            exec("ALTER TABLE history ADD COLUMN IF NOT EXISTS channel_avatar TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE history ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE favorites ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE watch_later ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE progress ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE search_history ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlists ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE playlist_videos ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE settings ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE blocked_channels ADD COLUMN IF NOT EXISTS scope TEXT NOT NULL DEFAULT 'user'")
            exec("ALTER TABLE blocked_videos ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT ''")
            exec("ALTER TABLE blocked_videos ADD COLUMN IF NOT EXISTS scope TEXT NOT NULL DEFAULT 'user'")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_type TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_code TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS public_username TEXT")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT")
            exec("ALTER TABLE recommendation_events ADD COLUMN IF NOT EXISTS watch_duration_ms BIGINT")
            exec("ALTER TABLE youtube_takeout_import_jobs ADD COLUMN IF NOT EXISTS preview_json TEXT")
            exec("ALTER TABLE bug_reports ALTER COLUMN github_issue_url TYPE TEXT")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS users_public_username_unique ON users (public_username)")
            DatabasePrimaryKeyMigrations.apply()
            DatabaseIndexMigrations.apply()
        }
    }

    suspend fun <T> query(block: () -> T): T =
        withContext(Dispatchers.IO) { transaction { block() } }
}
