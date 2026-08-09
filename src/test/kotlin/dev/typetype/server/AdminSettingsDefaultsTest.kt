package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.services.AdminSettingsService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminSettingsDefaultsTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    @Test
    fun `default settings provider seeds remote login only when settings row is missing`() = runTest {
        val settings = AdminSettingsService { AdminSettingsItem(youtubeRemoteLoginEnabled = true) }
        assertEquals(true, settings.get().youtubeRemoteLoginEnabled)

        settings.upsert(AdminSettingsItem(youtubeRemoteLoginEnabled = false))

        assertEquals(false, settings.get().youtubeRemoteLoginEnabled)
    }

    @Test
    fun `RSS public URL rejects embedded credentials`() = runTest {
        val error = runCatching {
            AdminSettingsService().upsert(
                AdminSettingsItem(rssPublicBaseUrl = "https://user:password@video.example"),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("RSS public base URL cannot contain credentials", error?.message)
    }

    @Test
    fun `RSS cannot be enabled without a public URL`() = runTest {
        val error = runCatching {
            AdminSettingsService().upsert(AdminSettingsItem(rssEnabled = true))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("RSS public base URL is required when RSS is enabled", error?.message)
    }
}
