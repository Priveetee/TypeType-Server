package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException

private const val BILIBILI_RANGE_ATTEMPTS = 3

internal fun readBilibiliRangeWithRetry(client: OkHttpClient, request: Request): ExtractionResult<ProxyResponse> {
    var lastMessage = "Proxy fetch failed"
    for (attempt in 1..BILIBILI_RANGE_ATTEMPTS) {
        runCatching { client.newCall(request).execute() }
            .onSuccess { response ->
                response.use {
                    val result = it.readBilibiliRangeBytes()
                    when (result) {
                        is ExtractionResult.Success -> return result
                        is ExtractionResult.BadRequest -> return result
                        is ExtractionResult.Failure -> lastMessage = result.message
                    }
                }
            }
            .onFailure { lastMessage = it.message ?: "Proxy fetch failed" }
        if (attempt == BILIBILI_RANGE_ATTEMPTS) break
    }
    return ExtractionResult.Failure(lastMessage)
}

private fun Response.readBilibiliRangeBytes(): ExtractionResult<ProxyResponse> {
    if (!isSuccessful && code != 206) return ExtractionResult.Failure("Upstream returned $code")
    val bytes = try {
        body.bytes()
    } catch (e: IOException) {
        return ExtractionResult.Failure(e.message ?: "Proxy fetch failed")
    }
    return ExtractionResult.Success(ProxyResponse(
        status = code,
        contentType = header("Content-Type") ?: "application/octet-stream",
        contentLength = bytes.size.toLong(),
        contentRange = header("Content-Range"),
        acceptRanges = header("Accept-Ranges"),
        stream = ByteArrayInputStream(bytes),
        close = {},
    ))
}
