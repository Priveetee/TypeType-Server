package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseIndexMigrations {
    fun apply() {
        exec("CREATE EXTENSION IF NOT EXISTS pg_trgm")
        exec("CREATE INDEX IF NOT EXISTS idx_history_user_watched_id ON history (user_id, watched_at DESC, id DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_playlist_videos_user_playlist_position ON playlist_videos (user_id, playlist_id, position)")
        exec("CREATE INDEX IF NOT EXISTS idx_subscriptions_user_subscribed_at ON subscriptions (user_id, subscribed_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_favorites_user_favorited_at ON favorites (user_id, favorited_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_watch_later_user_added_at ON watch_later (user_id, added_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_search_history_user_searched_at ON search_history (user_id, searched_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_playlists_user_created_at ON playlists (user_id, created_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_reco_feedback_user_created_at ON recommendation_feedback (user_id, created_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_reco_events_user_event_occurred ON recommendation_events (user_id, event_type, occurred_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_reco_events_user_occurred ON recommendation_events (user_id, occurred_at DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_reco_feed_history_user_last_shown ON recommendation_feed_history (user_id, last_shown DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_user_channel_interest_user_score ON user_channel_interest (user_id, score DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_user_topic_interest_user_score ON user_topic_interest (user_id, score DESC)")
        exec("CREATE INDEX IF NOT EXISTS idx_history_title_trgm ON history USING gin (lower(title) gin_trgm_ops)")
        exec("CREATE INDEX IF NOT EXISTS idx_history_channel_name_trgm ON history USING gin (lower(channel_name) gin_trgm_ops)")
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
