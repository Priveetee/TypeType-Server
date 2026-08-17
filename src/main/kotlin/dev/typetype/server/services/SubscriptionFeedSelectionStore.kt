package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import kotlinx.serialization.Serializable
import java.util.UUID

internal class SubscriptionFeedSelectionStore(
    private val cache: CacheService,
    private val subscriptions: SubscriptionsService,
) {
    suspend fun resolve(
        userId: String,
        selection: SubscriptionSelection,
        token: String?,
    ): SubscriptionFeedSelectionSnapshot? {
        if (selection == SubscriptionSelection.All) return SubscriptionFeedSelectionSnapshot(null, null)
        if (token == null) {
            val channelUrls = subscriptions.getChannelUrls(userId, selection)
            val nextToken = UUID.randomUUID().toString()
            cache.set(
                SubscriptionFeedCacheKeys.selection(userId, nextToken),
                CacheJson.encodeToString(
                    StoredSubscriptionFeedSelection.serializer(),
                    StoredSubscriptionFeedSelection(selection.cursorKey, channelUrls.toList()),
                ),
                SubscriptionFeedSnapshotStore.RETENTION_SECONDS,
            )
            return SubscriptionFeedSelectionSnapshot(nextToken, channelUrls)
        }
        val raw = runCatching { cache.get(SubscriptionFeedCacheKeys.selection(userId, token)) }.getOrNull()
            ?: return null
        val stored = runCatching {
            CacheJson.decodeFromString(StoredSubscriptionFeedSelection.serializer(), raw)
        }.getOrNull() ?: return null
        if (stored.filterKey != selection.cursorKey) return null
        return SubscriptionFeedSelectionSnapshot(token, stored.channelUrls.toSet())
    }
}

@Serializable
private data class StoredSubscriptionFeedSelection(
    val filterKey: String,
    val channelUrls: List<String>,
)

internal data class SubscriptionFeedSelectionSnapshot(
    val token: String?,
    val channelUrls: Set<String>?,
)
