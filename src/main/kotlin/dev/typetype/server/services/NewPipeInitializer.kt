package dev.typetype.server.services

import dev.typetype.server.downloader.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder

object NewPipeInitializer {
    @Volatile private var initialized = false
    @Volatile private var decoderServiceUrl: String? = null

    fun init(tokenServiceUrl: String? = null): Unit {
        val normalizedUrl = tokenServiceUrl?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedUrl != null && normalizedUrl != decoderServiceUrl) {
            YoutubeApiDecoder.setLocalDecoder(TypetypeTokenYoutubeJavaScriptDecoder(normalizedUrl))
            decoderServiceUrl = normalizedUrl
        }
        if (initialized) return
        NewPipe.init(OkHttpDownloader.instance())
        initialized = true
    }
}
