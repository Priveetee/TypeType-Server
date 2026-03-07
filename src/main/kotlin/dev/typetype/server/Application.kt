package dev.typetype.server

import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeTrendingService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.schabi.newpipe.extractor.NewPipe

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    NewPipe.init(OkHttpDownloader.instance())

    configurePlugins()

    val streamService = PipePipeStreamService()
    val searchService = PipePipeSearchService()
    val trendingService = PipePipeTrendingService()

    routing {
        streamRoutes(streamService)
        searchRoutes(searchService)
        trendingRoutes(trendingService)
    }
}
