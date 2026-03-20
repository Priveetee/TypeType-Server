package dev.typetype.server.services

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class PipePipeBackupSqliteReader {

    fun read(sqlitePath: Path): PipePipeBackupSnapshotItem {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:$sqlitePath").use { sqlite ->
            PipePipeBackupSnapshotItem(
                subscriptions = readSubscriptions(sqlite),
                history = readHistory(sqlite),
                playlists = readPlaylists(sqlite),
                progress = readProgress(sqlite),
                searchHistory = readSearchHistory(sqlite),
            )
        }
    }

    private fun readSubscriptions(sqlite: Connection): List<PipePipeBackupSubscriptionItem> {
        val sql = "SELECT service_id, url, name, avatar_url FROM subscriptions"
        return sqlite.query(sql) { PipePipeBackupSubscriptionItem(it.int("service_id"), it.str("url"), it.str("name"), it.str("avatar_url")) }
    }

    private fun readHistory(sqlite: Connection): List<PipePipeBackupHistoryItem> {
        val sql = """
            SELECT sh.access_date, s.url, s.title, s.duration, s.uploader, s.uploader_url, s.thumbnail_url
            FROM stream_history sh
            INNER JOIN streams s ON s.uid = sh.stream_id
            ORDER BY sh.access_date DESC
        """.trimIndent()
        return sqlite.query(sql) {
            PipePipeBackupHistoryItem(it.long("access_date"), it.str("url"), it.str("title"), it.long("duration"), it.str("uploader"), it.str("uploader_url"), it.str("thumbnail_url"))
        }
    }

    private fun readPlaylists(sqlite: Connection): List<PipePipeBackupPlaylistItem> {
        val names = sqlite.query("SELECT uid, name FROM playlists ORDER BY display_index ASC, uid ASC") { it.long("uid") to it.str("name").ifBlank { "Playlist" } }.toMap()
        val videos = sqlite.query(
            """
                SELECT ps.playlist_id, ps.join_index, s.url, s.title, s.duration, s.thumbnail_url
                FROM playlist_stream_join ps
                INNER JOIN streams s ON s.uid = ps.stream_id
                ORDER BY ps.playlist_id ASC, ps.join_index ASC
            """.trimIndent()
        ) { it.long("playlist_id") to PipePipeBackupPlaylistVideoItem(it.str("url"), it.str("title"), it.long("duration"), it.str("thumbnail_url"), it.int("join_index")) }
        val grouped = videos.groupBy({ p -> p.first }, { p -> p.second })
        return names.map { (id, name) -> PipePipeBackupPlaylistItem(name = name, videos = grouped[id].orEmpty().sortedBy { it.position }) }
    }

    private fun readProgress(sqlite: Connection): List<PipePipeBackupProgressItem> {
        val sql = """
            SELECT s.url, ss.progress_time
            FROM stream_state ss
            INNER JOIN streams s ON s.uid = ss.stream_id
        """.trimIndent()
        return sqlite.query(sql) { PipePipeBackupProgressItem(it.str("url"), it.long("progress_time")) }
    }

    private fun readSearchHistory(sqlite: Connection): List<PipePipeBackupSearchHistoryItem> {
        val sql = "SELECT search, creation_date FROM search_history ORDER BY creation_date DESC"
        return sqlite.query(sql) { PipePipeBackupSearchHistoryItem(it.str("search"), it.long("creation_date")) }
    }

    private fun <T> Connection.query(sql: String, map: (Row) -> T): List<T> =
        prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                val items = mutableListOf<T>()
                while (rs.next()) items += map(Row(rs))
                items
            }
        }

    private class Row(private val rs: java.sql.ResultSet) {
        fun str(name: String): String = rs.getString(name) ?: ""
        fun int(name: String): Int = rs.getInt(name)
        fun long(name: String): Long = rs.getLong(name)
    }
}
