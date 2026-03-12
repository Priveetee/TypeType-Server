package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.SuggestionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedSuggestionServiceTest {

    private val delegate: SuggestionService = mockk()
    private val cache: CacheService = mockk()
    private val service = CachedSuggestionService(delegate, cache)

    @Test
    fun `cache hit returns cached suggestions without calling delegate`() = runBlocking {
        coEvery { cache.get("suggestions:0:rick") } returns """["rick astley","rickroll"]"""
        val result = service.getSuggestions("rick", 0)
        assertEquals(ExtractionResult.Success(listOf("rick astley", "rickroll")), result)
        coVerify(exactly = 0) { delegate.getSuggestions(any(), any()) }
    }

    @Test
    fun `cache miss delegates and stores result`() = runBlocking {
        coEvery { cache.get("suggestions:0:rick") } returns null
        coEvery { delegate.getSuggestions("rick", 0) } returns ExtractionResult.Success(listOf("rick astley"))
        coEvery { cache.set(any(), any(), any()) } returns Unit
        val result = service.getSuggestions("rick", 0)
        assertEquals(ExtractionResult.Success(listOf("rick astley")), result)
        coVerify(exactly = 1) { cache.set("suggestions:0:rick", any(), 300L) }
    }

    @Test
    fun `delegate failure is not cached`() = runBlocking {
        coEvery { cache.get("suggestions:0:bad") } returns null
        coEvery { delegate.getSuggestions("bad", 0) } returns ExtractionResult.Failure("network error")
        val result = service.getSuggestions("bad", 0)
        assertEquals(ExtractionResult.Failure("network error"), result)
        coVerify(exactly = 0) { cache.set(any(), any(), any()) }
    }
}
