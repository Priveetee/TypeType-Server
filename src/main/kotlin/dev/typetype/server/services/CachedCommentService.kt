package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.serialization.json.Json

class CachedCommentService(
    private val delegate: CommentService,
    private val cache: CacheService,
) : CommentService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> {
        val key = "comments:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(json.decodeFromString<CommentsPageResponse>(cached)) }.getOrElse {
                delegate.getComments(url, nextpage)
            }
        }
        val result = delegate.getComments(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, json.encodeToString(CommentsPageResponse.serializer(), result.data), 300L) }
        }
        return result
    }
}
