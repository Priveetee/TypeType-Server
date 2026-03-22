package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class RecommendationInterestService {
    suspend fun update(userId: String, eventType: String, uploaderUrl: String?, title: String?, watchRatio: Double?) {
        val weight = RecommendationInterestWeight.of(eventType, watchRatio)
        if (weight == 0.0) return
        val now = System.currentTimeMillis()
        if (!uploaderUrl.isNullOrBlank()) upsertChannel(userId, uploaderUrl, weight, now)
        RecommendationTopicTokenizer.tokenize(title.orEmpty()).forEach { topic ->
            upsertTopic(userId, topic, weight, now)
        }
    }

    private suspend fun upsertChannel(userId: String, uploaderUrl: String, delta: Double, now: Long) = DatabaseFactory.query {
        val existing = UserChannelInterestTable.selectAll()
            .where { (UserChannelInterestTable.userId eq userId) and (UserChannelInterestTable.uploaderUrl eq uploaderUrl) }
            .singleOrNull()
        if (existing == null) {
            UserChannelInterestTable.insert {
                it[UserChannelInterestTable.userId] = userId
                it[UserChannelInterestTable.uploaderUrl] = uploaderUrl
                it[score] = delta
                it[updatedAt] = now
            }
        } else {
            UserChannelInterestTable.update({ (UserChannelInterestTable.userId eq userId) and (UserChannelInterestTable.uploaderUrl eq uploaderUrl) }) {
                it[score] = existing[UserChannelInterestTable.score] + delta
                it[updatedAt] = now
            }
        }
    }

    private suspend fun upsertTopic(userId: String, topic: String, delta: Double, now: Long) = DatabaseFactory.query {
        val existing = UserTopicInterestTable.selectAll()
            .where { (UserTopicInterestTable.userId eq userId) and (UserTopicInterestTable.topic eq topic) }
            .singleOrNull()
        if (existing == null) {
            UserTopicInterestTable.insert {
                it[UserTopicInterestTable.userId] = userId
                it[UserTopicInterestTable.topic] = topic
                it[score] = delta
                it[updatedAt] = now
            }
        } else {
            UserTopicInterestTable.update({ (UserTopicInterestTable.userId eq userId) and (UserTopicInterestTable.topic eq topic) }) {
                it[score] = existing[UserTopicInterestTable.score] + delta
                it[updatedAt] = now
            }
        }
    }
}
