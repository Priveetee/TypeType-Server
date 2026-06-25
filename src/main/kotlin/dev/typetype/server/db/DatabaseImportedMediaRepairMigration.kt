package dev.typetype.server.db

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object DatabaseImportedMediaRepairMigration {
    fun apply() {
        repairVideoFields(table = "history", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairVideoFields(table = "playlist_videos", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairVideoFields(table = "watch_later", titleColumn = "title", thumbnailColumn = "thumbnail", urlColumn = "url")
        repairSubscriptionAvatars(table = "history")
        repairSubscriptionAvatars(table = "playlist_videos")
        repairChannelAvatars(table = "history")
        repairChannelAvatars(table = "playlist_videos")
    }

    private fun repairVideoFields(table: String, titleColumn: String, thumbnailColumn: String, urlColumn: String) {
        val videoId = videoIdExpression(urlColumn)
        exec("""
            UPDATE $table
            SET $thumbnailColumn = 'https://i.ytimg.com/vi/' || $videoId || '/hqdefault.jpg'
            WHERE $thumbnailColumn = '' AND $videoId IS NOT NULL
        """.trimIndent())
        exec("""
            UPDATE $table
            SET $titleColumn = 'YouTube video ' || $videoId
            WHERE $titleColumn = '' AND $videoId IS NOT NULL
        """.trimIndent())
    }

    private fun repairChannelAvatars(table: String) {
        exec("""
            UPDATE $table media
            SET channel_avatar = subscriptions.avatar_url
            FROM subscriptions
            WHERE media.channel_avatar = ''
              AND subscriptions.avatar_url <> ''
              AND media.user_id = subscriptions.user_id
              AND media.channel_url = subscriptions.channel_url
        """.trimIndent())
    }

    private fun repairSubscriptionAvatars(table: String) {
        exec("""
            UPDATE subscriptions
            SET avatar_url = media.channel_avatar
            FROM $table media
            WHERE subscriptions.avatar_url = ''
              AND media.channel_avatar <> ''
              AND media.user_id = subscriptions.user_id
              AND media.channel_url = subscriptions.channel_url
        """.trimIndent())
    }

    private fun videoIdExpression(urlColumn: String): String = "substring($urlColumn from '(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})')"

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }
}
