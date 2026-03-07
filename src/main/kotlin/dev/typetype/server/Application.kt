package dev.typetype.server

import dev.typetype.server.downloader.OkHttpDownloader
import dev.typetype.server.routes.channelRoutes
import dev.typetype.server.routes.commentRoutes
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.routes.trendingRoutes
import dev.typetype.server.services.PipePipeChannelService
import dev.typetype.server.services.PipePipeCommentService
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.PipePipeStreamService
import dev.typetype.server.services.PipePipeTrendingService
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    NewPipe.init(OkHttpDownloader.instance())

    val sponsorBlockSettings = SponsorBlockApiSettings().apply {
        includeSponsorCategory = true
        includeIntroCategory = true
        includeOutroCategory = true
        includeInteractionCategory = true
        includeHighlightCategory = true
        includeSelfPromoCategory = true
        includeMusicCategory = true
        includePreviewCategory = true
        includeFillerCategory = true
    }
    ServiceList.YouTube.setSponsorBlockApiSettings(sponsorBlockSettings)
    ServiceList.BiliBili.setSponsorBlockApiSettings(sponsorBlockSettings)

    configurePlugins()

    val streamService = PipePipeStreamService()
    val searchService = PipePipeSearchService()
    val trendingService = PipePipeTrendingService()
    val commentService = PipePipeCommentService()
    val channelService = PipePipeChannelService()

    routing {
        streamRoutes(streamService)
        searchRoutes(searchService)
        trendingRoutes(trendingService)
        commentRoutes(commentService)
        channelRoutes(channelService)
    }
}
