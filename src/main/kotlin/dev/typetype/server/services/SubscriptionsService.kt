package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class SubscriptionsService {

    suspend fun getAll(userId: String): List<SubscriptionItem> = DatabaseFactory.query {
        SubscriptionsTable.selectAll()
            .where { SubscriptionsTable.userId eq userId }
            .orderBy(SubscriptionsTable.subscribedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(userId: String, item: SubscriptionItem): SubscriptionItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            SubscriptionsTable.insert {
                it[SubscriptionsTable.userId] = userId
                it[channelUrl] = item.channelUrl
                it[name] = item.name
                it[avatarUrl] = item.avatarUrl
                it[subscribedAt] = now
            }
        }
        return item.copy(subscribedAt = now)
    }

    suspend fun delete(userId: String, channelUrl: String): Boolean = DatabaseFactory.query {
        SubscriptionsTable.deleteWhere { SubscriptionsTable.channelUrl eq channelUrl and (SubscriptionsTable.userId eq userId) } > 0
    }

    private fun ResultRow.toItem() = SubscriptionItem(
        channelUrl = this[SubscriptionsTable.channelUrl],
        name = this[SubscriptionsTable.name],
        avatarUrl = this[SubscriptionsTable.avatarUrl],
        subscribedAt = this[SubscriptionsTable.subscribedAt],
    )
}
