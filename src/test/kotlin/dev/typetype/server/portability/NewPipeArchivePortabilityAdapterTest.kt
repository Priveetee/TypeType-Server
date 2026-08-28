package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NewPipeArchivePortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `newpipe archive streams all supported tables`() {
        val input = input(9)
        val adapter = NewPipePortabilityAdapter()
        val spool = PortabilitySpool.create(directory)

        assertEquals(PortabilityFormat.NEW_PIPE, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)

        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(2L, spool.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.PROGRESS])
        assertEquals(1L, spool.counts()[PortabilityCategory.SEARCH_HISTORY])
        assertEquals(1L, spool.counts()[PortabilityCategory.SAVED_PLAYLISTS])
        spool.delete()
    }

    @Test
    fun `pipepipe archive is distinguished by schema version`() {
        val input = input(901)
        val adapter = PipePipePortabilityAdapter()

        assertEquals(PortabilityFormat.PIPE_PIPE, requireNotNull(adapter.detect(input)).format)
        assertEquals(null, NewPipePortabilityAdapter().detect(input))
    }

    @Test
    fun `pipepipe export round trips canonical records through its real archive`() {
        val source = PortabilitySpool.create(directory)
        source.write(PortabilitySubscription("https://youtube.com/channel/UC1", "One"))
        source.write(PortabilitySubscriptionGroup("News"))
        source.write(PortabilitySubscriptionGroupMembership("News", "https://youtube.com/channel/UC1"))
        val video = PortabilityVideo("https://youtube.com/watch?v=video000001", "Video", durationSeconds = 60)
        source.write(PortabilityHistory(video, 10))
        source.write(PortabilityPlaylist("local", "Local"))
        source.write(PortabilityPlaylistVideo("local", 0, video))
        source.write(PortabilityProgress(video.url, 20))
        source.write(PortabilitySearchHistory("query", 30))
        source.write(PortabilitySavedPlaylist("remote", "https://youtube.com/playlist?list=PL1", "Remote"))
        val output = directory.resolve("pipepipe-export.zip")
        val categories = source.categories()

        Files.newOutputStream(output).use {
            PipePipePortabilityAdapter().encode(source, it, categories)
        }
        assertValidRoomArchive(output, NewPipeArchiveTarget.PIPE_PIPE)
        val input = PortabilityInputFactory.create(output, output.fileName.toString(), "application/zip")
        assertEquals("901", PipePipePortabilityAdapter().detect(input)?.formatVersion)
        val restored = PortabilitySpool.create(directory)
        PipePipePortabilityAdapter().decode(input, restored)

        assertEquals(1L, restored.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(2L, restored.counts()[PortabilityCategory.SUBSCRIPTION_GROUPS])
        assertEquals(1L, restored.counts()[PortabilityCategory.HISTORY])
        assertEquals(2L, restored.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, restored.counts()[PortabilityCategory.PROGRESS])
        assertEquals(1L, restored.counts()[PortabilityCategory.SEARCH_HISTORY])
        assertEquals(1L, restored.counts()[PortabilityCategory.SAVED_PLAYLISTS])
        source.delete()
        restored.delete()
    }

    private fun assertValidRoomArchive(archive: Path, target: NewPipeArchiveTarget) {
        val database = directory.resolve("validated-${target.databaseVersion}.db")
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            check(zip.nextEntry?.name == "newpipe.db")
            Files.newOutputStream(database).use(zip::copyTo)
        }
        DriverManager.getConnection("jdbc:sqlite:$database").use { sqlite ->
            sqlite.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use {
                    assertEquals(target.databaseVersion, it.getInt(1))
                }
                statement.executeQuery("PRAGMA integrity_check").use {
                    assertEquals("ok", it.getString(1))
                }
                statement.executeQuery("PRAGMA foreign_key_check").use {
                    assertEquals(false, it.next())
                }
                statement.executeQuery("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
                    assertEquals(target.identityHash, it.getString(1))
                }
            }
        }
    }

    private fun input(version: Int): PortabilityInput {
        val db = directory.resolve("source-$version.db")
        DriverManager.getConnection("jdbc:sqlite:$db").use { sqlite ->
            sqlite.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = $version")
                statement.execute("CREATE TABLE subscriptions(uid INTEGER, service_id INTEGER, url TEXT, name TEXT, avatar_url TEXT)")
                statement.execute("CREATE TABLE streams(uid INTEGER, url TEXT, title TEXT, duration INTEGER, uploader TEXT, uploader_url TEXT, thumbnail_url TEXT)")
                statement.execute("CREATE TABLE stream_history(stream_id INTEGER, access_date INTEGER)")
                statement.execute("CREATE TABLE stream_state(stream_id INTEGER, progress_time INTEGER)")
                statement.execute("CREATE TABLE playlists(uid INTEGER, name TEXT, display_index INTEGER)")
                statement.execute("CREATE TABLE playlist_stream_join(playlist_id INTEGER, stream_id INTEGER, join_index INTEGER)")
                statement.execute("CREATE TABLE remote_playlists(uid INTEGER, url TEXT, name TEXT, thumbnail_url TEXT, uploader TEXT, stream_count INTEGER, display_index INTEGER)")
                statement.execute("CREATE TABLE feed_group(uid INTEGER, name TEXT, sort_order INTEGER)")
                statement.execute("CREATE TABLE feed_group_subscription_join(group_id INTEGER, subscription_id INTEGER)")
                statement.execute("CREATE TABLE search_history(id INTEGER, search TEXT, creation_date INTEGER)")
                statement.execute("INSERT INTO subscriptions VALUES (1, 0, 'https://youtube.com/channel/UC1', 'One', 'avatar')")
                statement.execute("INSERT INTO streams VALUES (1, 'https://youtube.com/watch?v=v1', 'Video', 60, 'One', 'https://youtube.com/channel/UC1', 'thumb')")
                statement.execute("INSERT INTO stream_history VALUES (1, 10)")
                statement.execute("INSERT INTO stream_state VALUES (1, 20)")
                statement.execute("INSERT INTO playlists VALUES (1, 'Local', 0)")
                statement.execute("INSERT INTO playlist_stream_join VALUES (1, 1, 0)")
                statement.execute("INSERT INTO remote_playlists VALUES (2, 'https://youtube.com/playlist?list=PL1', 'Remote', 'thumb', 'One', 1, 0)")
                statement.execute("INSERT INTO feed_group VALUES (1, 'News', 0)")
                statement.execute("INSERT INTO feed_group_subscription_join VALUES (1, 1)")
                statement.execute("INSERT INTO search_history VALUES (1, 'query', 30)")
            }
        }
        val archive = directory.resolve("backup-$version.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            output.putNextEntry(ZipEntry("newpipe.db"))
            Files.newInputStream(db).use { it.copyTo(output) }
            output.closeEntry()
        }
        return PortabilityInputFactory.create(archive, archive.fileName.toString(), "application/zip")
    }
}
