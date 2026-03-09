package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class SubscriptionsService {

    suspend fun getAll(): List<SubscriptionItem> = DatabaseFactory.query {
        SubscriptionsTable.selectAll()
            .orderBy(SubscriptionsTable.subscribedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(item: SubscriptionItem): SubscriptionItem {
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            SubscriptionsTable.insert {
                it[channelUrl] = item.channelUrl
                it[name] = item.name
                it[avatarUrl] = item.avatarUrl
                it[subscribedAt] = now
            }
        }
        return item.copy(subscribedAt = now)
    }

    suspend fun delete(channelUrl: String): Boolean = DatabaseFactory.query {
        SubscriptionsTable.deleteWhere { SubscriptionsTable.channelUrl eq channelUrl } > 0
    }

    private fun ResultRow.toItem() = SubscriptionItem(
        channelUrl = this[SubscriptionsTable.channelUrl],
        name = this[SubscriptionsTable.name],
        avatarUrl = this[SubscriptionsTable.avatarUrl],
        subscribedAt = this[SubscriptionsTable.subscribedAt],
    )
}
