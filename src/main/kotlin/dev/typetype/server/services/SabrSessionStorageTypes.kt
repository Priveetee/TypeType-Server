package dev.typetype.server.services

internal const val SABR_MAX_SEGMENT_MEMORY_BYTES = 64L * 1024L * 1024L

internal data class SabrReaderTrackKey(val generation: Long, val itag: Int)
