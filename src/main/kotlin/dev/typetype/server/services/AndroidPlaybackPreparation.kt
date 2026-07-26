package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

internal class AndroidPlaybackPreparation {
    private val job = AtomicReference<Job?>()
    private val failure = AtomicReference<AndroidDashManifestResult.TemporaryFailure?>()

    fun start(scope: CoroutineScope, block: suspend () -> Unit): Unit {
        val candidate = scope.launch(start = CoroutineStart.LAZY) { block() }
        if (job.compareAndSet(null, candidate)) candidate.start() else candidate.cancel()
    }

    fun fail(result: AndroidDashManifestResult.TemporaryFailure): Unit {
        failure.compareAndSet(null, result)
    }

    fun result(holder: SabrSessionHolder, manifests: AndroidDashManifestService): AndroidDashManifestResult =
        failure.get() ?: manifests.build(holder)

    fun cancel(): Unit {
        job.getAndSet(null)?.cancel()
    }
}

internal class AndroidPlaybackPreparationCoordinator(
    private val store: SabrSessionStore,
    private val manifests: AndroidDashManifestService,
    private val timeoutMs: Long = PREPARATION_TIMEOUT_MS,
    private val initializationTimeoutMs: Long = INITIALIZATION_TIMEOUT_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val sharedInitializer: suspend (SabrSessionHolder, YoutubeSabrFormat) -> ByteArray? =
        store::fetchInitializationData,
    private val exactInitializer: suspend (SabrSessionHolder, YoutubeSabrFormat, Long) -> ByteArray =
        store::fetchExactInitializationData,
) {
    fun start(session: AndroidPlaybackSession): Unit {
        if (manifests.build(session.holder) !is AndroidDashManifestResult.Preparing) return
        session.preparation.start(scope) { prepare(session) }
    }

    private suspend fun prepare(session: AndroidPlaybackSession): Unit {
        val holder = session.holder
        val startedAt = System.currentTimeMillis()
        try {
            withTimeout(timeoutMs) {
                initializeFromPlaybackSession(holder)
                initializeExactlyIfMissing(holder, holder.videoFormat)
                initializeExactlyIfMissing(holder, holder.audioFormat)
            }
            val result = manifests.build(holder)
            if (result is AndroidDashManifestResult.Preparing) {
                session.preparation.fail(temporaryFailure(PREPARATION_FAILED))
            }
            logger.info(
                "android_playback_preparation videoId={} videoItag={} audioItag={} elapsedMs={} result={}",
                holder.key.videoId,
                holder.videoFormat.itag,
                holder.audioFormat.itag,
                System.currentTimeMillis() - startedAt,
                result::class.simpleName,
            )
        } catch (error: TimeoutCancellationException) {
            session.preparation.fail(temporaryFailure(PREPARATION_TIMEOUT))
            logFailure(holder, startedAt, error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            session.preparation.fail(temporaryFailure(PREPARATION_FAILED))
            logFailure(holder, startedAt, error)
        } catch (error: RuntimeException) {
            session.preparation.fail(temporaryFailure(PREPARATION_FAILED))
            logFailure(holder, startedAt, error)
        }
    }

    private suspend fun initializeFromPlaybackSession(holder: SabrSessionHolder): Unit {
        try {
            initializeSharedIfMissing(holder, holder.videoFormat)
            initializeSharedIfMissing(holder, holder.audioFormat)
        } catch (_: IOException) {
            // The exact initialization path below remains a bounded fallback.
        }
    }

    private suspend fun initializeSharedIfMissing(holder: SabrSessionHolder, format: YoutubeSabrFormat): Unit {
        if (holder.session.streamState.hasSegmentIndex(format)) return
        sharedInitializer(holder, format)
    }

    private suspend fun initializeExactlyIfMissing(holder: SabrSessionHolder, format: YoutubeSabrFormat): Unit {
        if (holder.session.streamState.hasSegmentIndex(format)) return
        exactInitializer(holder, format, initializationTimeoutMs)
        if (!holder.session.streamState.hasSegmentIndex(format)) {
            throw IOException("Exact initialization did not produce a segment index for itag ${format.itag}")
        }
    }

    private fun logFailure(holder: SabrSessionHolder, startedAt: Long, error: Throwable): Unit {
        logger.warn(
            "android_playback_preparation_failed videoId={} videoItag={} audioItag={} elapsedMs={} errorClass={}",
            holder.key.videoId,
            holder.videoFormat.itag,
            holder.audioFormat.itag,
            System.currentTimeMillis() - startedAt,
            error::class.simpleName,
        )
    }

    private fun temporaryFailure(code: String): AndroidDashManifestResult.TemporaryFailure =
        AndroidDashManifestResult.TemporaryFailure(code, "Android playback preparation is temporarily unavailable")

    private companion object {
        val logger = LoggerFactory.getLogger(AndroidPlaybackPreparationCoordinator::class.java)
        const val PREPARATION_TIMEOUT_MS = 8_000L
        const val INITIALIZATION_TIMEOUT_MS = 3_500L
        const val PREPARATION_TIMEOUT = "android_playback_preparation_timeout"
        const val PREPARATION_FAILED = "android_playback_preparation_failed"
    }
}
