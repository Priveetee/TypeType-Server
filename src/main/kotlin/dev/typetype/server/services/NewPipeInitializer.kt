package dev.typetype.server.services

import dev.typetype.server.downloader.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

object NewPipeInitializer {
    fun init(): Unit {
        NewPipe.init(OkHttpDownloader.instance())
    }
}
