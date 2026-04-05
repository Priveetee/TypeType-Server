package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.BlockedChannelsTable
import dev.typetype.server.db.tables.BlockedVideosTable
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.AdminSettingsTable
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.db.tables.NotificationStatesTable
import dev.typetype.server.db.tables.SearchHistoryTable
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.SessionsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.db.tables.RecommendationFeedbackTable
import dev.typetype.server.db.tables.RecommendationEventsTable
import dev.typetype.server.db.tables.RecommendationFeedHistoryTable
import dev.typetype.server.db.tables.RecommendationOnboardingPreferencesTable
import dev.typetype.server.db.tables.RecommendationOnboardingStateTable
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

const val TEST_USER_ID = "test-user-id"

object TestDatabase {

    @Volatile
    private var initialized = false

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    fun setup() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            DatabaseFactory.init(container.jdbcUrl, container.username, container.password)
            initialized = true
        }
    }

    fun truncateAll() = transaction {
        PlaylistVideosTable.deleteAll()
        PlaylistsTable.deleteAll()
        HistoryTable.deleteAll()
        FavoritesTable.deleteAll()
        SettingsTable.deleteAll()
        SubscriptionsTable.deleteAll()
        WatchLaterTable.deleteAll()
        ProgressTable.deleteAll()
        SearchHistoryTable.deleteAll()
        SessionsTable.deleteAll()
        PasswordResetTable.deleteAll()
        UsersTable.deleteAll()
        AdminSettingsTable.deleteAll()
        BlockedChannelsTable.deleteAll()
        BlockedVideosTable.deleteAll()
        RecommendationFeedbackTable.deleteAll()
        RecommendationEventsTable.deleteAll()
        RecommendationFeedHistoryTable.deleteAll()
        RecommendationOnboardingPreferencesTable.deleteAll()
        RecommendationOnboardingStateTable.deleteAll()
        UserChannelInterestTable.deleteAll()
        UserTopicInterestTable.deleteAll()
        YoutubeTakeoutImportJobsTable.deleteAll()
        YoutubeTakeoutPlaylistKeysTable.deleteAll()
        BugReportsTable.deleteAll()
        NotificationStatesTable.deleteAll()
    }
}
