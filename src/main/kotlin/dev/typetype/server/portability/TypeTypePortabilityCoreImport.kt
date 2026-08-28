package dev.typetype.server.portability

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.services.ChannelUrlCanonicalizer
import dev.typetype.server.services.SubscriptionMutationLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

internal object TypeTypePortabilityCoreImport {
    fun write(
        userId: String,
        category: PortabilityCategory,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long = when (category) {
        PortabilityCategory.SUBSCRIPTIONS -> subscriptions(userId, source, policy)
        PortabilityCategory.SUBSCRIPTION_GROUPS -> groups(userId, source, policy)
        PortabilityCategory.HISTORY -> history(userId, source, policy)
        PortabilityCategory.PLAYLISTS -> playlists(userId, source, policy)
        else -> error("Unsupported core portability category")
    }

    private fun subscriptions(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long {
        SubscriptionMutationLock.acquire(userId)
        if (policy == PortabilityDuplicatePolicy.REPLACE) {
            SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId }
        }
        var count = 0L
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            if (record !is PortabilitySubscription) return@forEach
            val channelUrl = ChannelUrlCanonicalizer.canonicalize(record.channelUrl)
            count += SubscriptionsTable.insertIgnore {
                it[SubscriptionsTable.userId] = userId
                it[SubscriptionsTable.channelUrl] = channelUrl
                it[SubscriptionsTable.name] = record.name
                it[SubscriptionsTable.avatarUrl] = record.avatarUrl
                it[SubscriptionsTable.subscribedAt] = record.subscribedAt
            }.insertedCount
        }
        return count
    }

    private fun groups(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long {
        SubscriptionMutationLock.acquire(userId)
        if (policy == PortabilityDuplicatePolicy.REPLACE) {
            SubscriptionGroupMembershipsTable.deleteWhere { SubscriptionGroupMembershipsTable.userId eq userId }
            SubscriptionGroupsTable.deleteWhere { SubscriptionGroupsTable.userId eq userId }
        }
        var count = 0L
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            val normalized = record.name.trim().lowercase(Locale.ROOT)
            require(normalized.isNotBlank() && normalized.length <= 100) { "Invalid subscription group name" }
            count += SubscriptionGroupsTable.insertIgnore {
                it[id] = stableId(userId, "group:$normalized")
                it[SubscriptionGroupsTable.userId] = userId
                it[name] = record.name.trim()
                it[normalizedName] = normalized
                it[createdAt] = 0L
                it[updatedAt] = 0L
            }.insertedCount
        }
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroupMembership) return@forEach
            val normalized = record.groupName.trim().lowercase(Locale.ROOT)
            val groupId = SubscriptionGroupsTable.selectAll().where {
                (SubscriptionGroupsTable.userId eq userId) and
                    (SubscriptionGroupsTable.normalizedName eq normalized)
            }.singleOrNull()?.get(SubscriptionGroupsTable.id) ?: return@forEach
            val channelUrl = ChannelUrlCanonicalizer.canonicalize(record.channelUrl)
            val subscribed = SubscriptionsTable.selectAll().where {
                (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.channelUrl eq channelUrl)
            }.empty().not()
            if (!subscribed) return@forEach
            count += SubscriptionGroupMembershipsTable.insertIgnore {
                it[SubscriptionGroupMembershipsTable.groupId] = groupId
                it[SubscriptionGroupMembershipsTable.userId] = userId
                it[SubscriptionGroupMembershipsTable.channelUrl] = channelUrl
                it[addedAt] = 0L
            }.insertedCount
        }
        return count
    }

    private fun history(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long {
        if (policy == PortabilityDuplicatePolicy.REPLACE) HistoryTable.deleteWhere { HistoryTable.userId eq userId }
        var count = 0L
        source.forEach(PortabilityCategory.HISTORY) { record ->
            if (record !is PortabilityHistory) return@forEach
            val exists = HistoryTable.selectAll().where {
                (HistoryTable.userId eq userId) and
                    (HistoryTable.url eq record.video.url) and
                    (HistoryTable.watchedAt eq record.watchedAt)
            }.empty().not()
            if (!exists) {
                count += HistoryTable.insertIgnore {
                    it[id] = UUID.randomUUID().toString()
                    it[HistoryTable.userId] = userId
                    it[url] = record.video.url
                    it[title] = record.video.title
                    it[thumbnail] = record.video.thumbnailUrl
                    it[channelName] = record.video.channelName
                    it[channelUrl] = record.video.channelUrl
                    it[channelAvatar] = record.video.channelAvatarUrl
                    it[duration] = record.video.durationSeconds
                    it[progress] = record.positionSeconds
                    it[watchedAt] = record.watchedAt
                }.insertedCount
            }
        }
        return count
    }

    private fun playlists(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
    ): Long {
        if (policy == PortabilityDuplicatePolicy.REPLACE) {
            PlaylistVideosTable.deleteWhere { PlaylistVideosTable.userId eq userId }
            PlaylistsTable.deleteWhere { PlaylistsTable.userId eq userId }
        }
        var count = 0L
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            if (record is PortabilityPlaylist) count += insertPlaylist(userId, record)
        }
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            if (record is PortabilityPlaylistVideo) count += insertPlaylistVideo(userId, record)
        }
        return count
    }

    private fun insertPlaylist(userId: String, record: PortabilityPlaylist): Int = PlaylistsTable.insertIgnore {
        it[id] = stableId(userId, "playlist:${record.sourceId}")
        it[PlaylistsTable.userId] = userId
        it[name] = record.name
        it[description] = record.description
        it[createdAt] = record.createdAt
    }.insertedCount

    private fun insertPlaylistVideo(userId: String, record: PortabilityPlaylistVideo): Int =
        PlaylistVideosTable.insertIgnore {
            it[id] = stableId(userId, "playlist:${record.playlistSourceId}:${record.position}:${record.video.url}")
            it[playlistId] = stableId(userId, "playlist:${record.playlistSourceId}")
            it[PlaylistVideosTable.userId] = userId
            it[url] = record.video.url
            it[title] = record.video.title
            it[thumbnail] = record.video.thumbnailUrl
            it[duration] = record.video.durationSeconds
            it[position] = record.position
            it[channelName] = record.video.channelName
            it[channelUrl] = record.video.channelUrl
            it[channelAvatar] = record.video.channelAvatarUrl
            it[viewCount] = record.video.viewCount
            it[addedAt] = record.addedAt
            it[publishedAt] = record.video.publishedAt
        }.insertedCount
}

private fun stableId(userId: String, value: String): String = UUID.nameUUIDFromBytes(
    "$userId:$value".toByteArray(StandardCharsets.UTF_8),
).toString()
