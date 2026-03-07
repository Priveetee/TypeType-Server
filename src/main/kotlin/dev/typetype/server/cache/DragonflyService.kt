package dev.typetype.server.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DragonflyService(url: String) : CacheService {

    private val commands: RedisCommands<String, String> =
        RedisClient.create(url).connect().sync()

    override suspend fun get(key: String): String? =
        withContext(Dispatchers.IO) { commands.get(key) }

    override suspend fun set(key: String, value: String, ttlSeconds: Long): Unit =
        withContext(Dispatchers.IO) { commands.setex(key, ttlSeconds, value) }

    override suspend fun delete(key: String): Unit =
        withContext(Dispatchers.IO) { commands.del(key) }
}
