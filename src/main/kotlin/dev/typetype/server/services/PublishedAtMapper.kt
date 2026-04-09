package dev.typetype.server.services

internal object PublishedAtMapper {
    fun fromUploaded(uploaded: Long): Long? = uploaded.takeIf { it > 0L }
}
