package dev.typetype.server

import dev.typetype.server.cache.DragonflyService
import dev.typetype.server.services.AndroidSubtitleHttpClient
import dev.typetype.server.services.AndroidSubtitleService
import dev.typetype.server.services.BilibiliRelatedService
import dev.typetype.server.services.BilibiliTrendingService
import dev.typetype.server.services.CachedChannelService
import dev.typetype.server.services.CachedCommentService
import dev.typetype.server.services.CachedManifestService
import dev.typetype.server.services.CachedNativeManifestService
import dev.typetype.server.services.CachedPodcastService
import dev.typetype.server.services.CachedPublicPlaylistService
import dev.typetype.server.services.CachedSearchService
import dev.typetype.server.services.CachedStreamService
import dev.typetype.server.services.CachedSuggestionService
import dev.typetype.server.services.CachedTrendingService
import dev.typetype.server.services.HlsManifestService
import dev.typetype.server.services.ManifestService
import dev.typetype.server.services.NativeManifestService
import dev.typetype.server.services.NicoNicoTrendingService
import dev.typetype.server.services.NicoVideoProxyService
import dev.typetype.server.services.OkHttpProxyService
import dev.typetype.server.services.PipePipeBulletCommentService
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipePodcastService
import dev.typetype.server.services.PipePipePublicPlaylistService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeSuggestionService
import dev.typetype.server.services.PipePipeTrendingService
import dev.typetype.server.services.SabrFallbackStreamService
import dev.typetype.server.services.SabrBootstrapStreamService
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.SignedHlsManifestTokenService
import dev.typetype.server.services.TypetypeTokenYoutubeSessionClient
import dev.typetype.server.services.YouTubeSubtitleService
import dev.typetype.server.services.YoutubePlayerClient
import dev.typetype.server.services.YoutubePlayerClientFallbackStreamService
import dev.typetype.server.services.YoutubePlayerClientStreamService
import dev.typetype.server.services.YoutubeScopedChannelService
import dev.typetype.server.services.YoutubeScopedCommentService
import dev.typetype.server.services.YoutubeScopedPublicPlaylistService
import dev.typetype.server.services.YoutubeScopedSearchService
import dev.typetype.server.services.YoutubeScopedStreamService
import dev.typetype.server.services.YoutubeScopedSuggestionService
import dev.typetype.server.services.YoutubeScopedTrendingService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionHlsManifestService
import dev.typetype.server.services.YoutubeSessionService
import dev.typetype.server.services.YoutubeSessionStreamService
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.Proxy
import java.net.ProxySelector
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class ExtractionServiceRegistry(
    cache: DragonflyService,
    subtitleServiceUrl: String,
    youtubeSessionEncryptionKey: String?,
    hlsManifestUrlSigner: ((String) -> String)? = null,
    youtubeProxySelector: ProxySelector? = null,
) {
    private val youtubeSessionSecret = youtubeSessionEncryptionKey?.trim()
        ?.takeIf { it.length >= MIN_YOUTUBE_SESSION_SECRET_LENGTH }
    val httpClient = OkHttpClient.Builder()
        .apply { youtubeProxySelector?.let(::proxySelector) }
        .build()
    val proxyHttpClient: OkHttpClient = httpClient.newBuilder()
        .dispatcher(proxyDispatcher())
        .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val sabrSessionStore = SabrSessionStore(subtitleServiceUrl, initCache = cache)
    val androidSabrSessionStore = SabrSessionStore(
        subtitleServiceUrl,
        idleEviction = Duration.ofMinutes(6),
        initCache = cache,
    )
    val youtubeSubtitleService = YouTubeSubtitleService(httpClient, subtitleServiceUrl)
    val androidSubtitleService = AndroidSubtitleService(
        youtubeSubtitleService,
        AndroidSubtitleHttpClient(
            httpClient.newBuilder()
                .followRedirects(false)
                .callTimeout(10, TimeUnit.SECONDS)
                .build(),
            youtubeProxySelector?.let {
                httpClient.newBuilder()
                    .proxy(Proxy.NO_PROXY)
                    .followRedirects(false)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .build()
            },
        ),
    )
    private val classicPipePipeStreamService = PipePipeStreamService(
        cache,
        youtubeSubtitleService,
        BilibiliRelatedService(),
    )
    private val sabrPipePipeStreamService = PipePipeStreamService(
        cache,
        youtubeSubtitleService,
        BilibiliRelatedService(),
        sabrSessionStore::rememberExtractedInfo,
    )
    private val classicPublicStreamService = YoutubePlayerClientFallbackStreamService(
        classicPipePipeStreamService,
        listOf(YoutubePlayerClient.ANDROID_VR, YoutubePlayerClient.WEB_SAFARI, YoutubePlayerClient.TV_SIMPLY),
    )
    private val classicAuthenticatedStreamService = YoutubePlayerClientFallbackStreamService(
        classicPipePipeStreamService,
        listOf(YoutubePlayerClient.TV_DOWNGRADED, YoutubePlayerClient.WEB_SAFARI),
    )
    private val sabrPublicStreamService = YoutubePlayerClientStreamService(
        sabrPipePipeStreamService,
        YoutubePlayerClient.MWEB,
    )
    val youtubeSessionService = YoutubeSessionService(youtubeSessionSecret?.let(YoutubeSessionCrypto::fromSecret))
    private val hlsTokenService = youtubeSessionSecret?.let(::SignedHlsManifestTokenService)
    private val tokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(subtitleServiceUrl, httpClient)
    val youtubeSessionStreamService = hlsTokenService?.let {
        YoutubeSessionStreamService(classicAuthenticatedStreamService, youtubeSessionService, cache, it)
    }
    val legacyStreamService = CachedStreamService(
        YoutubeScopedStreamService(classicPublicStreamService),
        cache,
        "stream-youtube-legacy:v5",
    )
    val youtubeSabrStreamService = CachedStreamService(
        YoutubeScopedStreamService(
            SabrFallbackStreamService(sabrPublicStreamService, sabrSessionStore, tokenYoutubeSessionClient),
        ),
        cache,
        "stream-youtube-sabr:v1",
    )
    val youtubeSabrBootstrapStreamService = CachedStreamService(
        YoutubeScopedStreamService(SabrBootstrapStreamService(sabrSessionStore, tokenYoutubeSessionClient)),
        cache,
        "stream-youtube-sabr-bootstrap:v1",
    )
    val nicoNicoStreamService = CachedStreamService(classicPipePipeStreamService, cache, "stream-niconico:v1")
    val bilibiliStreamService = CachedStreamService(classicPipePipeStreamService, cache, "stream-bilibili:v1")
    val streamService = CachedStreamService(classicPublicStreamService, cache, "stream-classic:v2")
    val searchService = CachedSearchService(YoutubeScopedSearchService(PipePipeSearchService()), cache)
    val trendingService = CachedTrendingService(
        YoutubeScopedTrendingService(PipePipeTrendingService(BilibiliTrendingService(), NicoNicoTrendingService(httpClient))),
        cache,
    )
    val commentService = CachedCommentService(YoutubeScopedCommentService(PipePipeCommentService()), cache)
    val bulletCommentService = PipePipeBulletCommentService()
    val channelService = CachedChannelService(YoutubeScopedChannelService(PipePipeChannelService()), cache)
    val podcastService = CachedPodcastService(PipePipePodcastService(), cache)
    val publicPlaylistService = CachedPublicPlaylistService(
        YoutubeScopedPublicPlaylistService(PipePipePublicPlaylistService()),
        cache,
    )
    val proxyService = OkHttpProxyService(proxyHttpClient)
    val nicoVideoProxyService = NicoVideoProxyService()
    val manifestService = CachedManifestService(ManifestService(streamService), cache)
    val nativeManifestService = CachedNativeManifestService(NativeManifestService(), cache)
    val hlsManifestService = HlsManifestService(
        streamService,
        proxyHttpClient,
        cache,
        hlsManifestUrlSigner,
        tokenYoutubeSessionClient::fetchHlsManifestUrl,
    )
    val youtubeSessionHlsManifestService = hlsTokenService?.let { tokenService ->
        youtubeSessionStreamService?.let {
            YoutubeSessionHlsManifestService(youtubeSessionService, it, hlsManifestService, tokenService)
        }
    }
    val suggestionService = CachedSuggestionService(YoutubeScopedSuggestionService(PipePipeSuggestionService()), cache)

    private companion object {
        const val MIN_YOUTUBE_SESSION_SECRET_LENGTH = 32

        fun proxyDispatcher(): Dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 64
        }
    }
}
