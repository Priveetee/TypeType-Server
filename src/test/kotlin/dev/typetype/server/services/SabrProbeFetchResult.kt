package dev.typetype.server.services

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment

internal data class SabrProbeFetchResult(
    val segment: SabrMediaSegment?,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val error: Throwable?,
)
