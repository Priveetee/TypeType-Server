package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult

class CachedCommentService(
    private val delegate: CommentService,
    private val cache: CacheService,
) : CommentService {

    override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> {
        val key = "comments:$url:${nextpage ?: "null"}"
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            return runCatching { ExtractionResult.Success(CacheJson.decodeFromString<CommentsPageResponse>(cached)) }.getOrElse {
                delegate.getComments(url, nextpage)
            }
        }
        val result = delegate.getComments(url, nextpage)
        if (result is ExtractionResult.Success) {
            runCatching { cache.set(key, CacheJson.encodeToString(CommentsPageResponse.serializer(), result.data), 300L) }
        }
        return result
    }
}

