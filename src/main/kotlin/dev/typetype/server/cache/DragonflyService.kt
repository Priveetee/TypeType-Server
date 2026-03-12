package dev.typetype.server.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

class DragonflyService(url: String) : CacheService {

    private val connection: StatefulRedisConnection<String, String> =
        RedisClient.create(url).connect()

    private val async: RedisAsyncCommands<String, String> = connection.async()

    override suspend fun get(key: String): String? = async.get(key).await()

    override suspend fun set(key: String, value: String, ttlSeconds: Long): Unit =
        async.setex(key, ttlSeconds, value).await().let {}

    override suspend fun delete(key: String): Unit =
        async.del(key).await().let {}
}
