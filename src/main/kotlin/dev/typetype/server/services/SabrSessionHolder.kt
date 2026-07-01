package dev.typetype.server.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class SabrSessionHolder(
    val session: YoutubeSabrSession,
    val info: YoutubeSabrInfo,
    val audioFormat: YoutubeSabrFormat,
    val videoFormat: YoutubeSabrFormat,
    val sessionToken: String,
    @Volatile var lastRequestAt: Instant,
    val pendingSignals: MutableSet<SabrSegmentRequest> = ConcurrentHashMap.newKeySet(),
    val pendingAwaits: ConcurrentHashMap<SabrSegmentRequest, CompletableDeferred<Unit>> = ConcurrentHashMap(),
    val pumpMutex: Mutex = Mutex(),
)
