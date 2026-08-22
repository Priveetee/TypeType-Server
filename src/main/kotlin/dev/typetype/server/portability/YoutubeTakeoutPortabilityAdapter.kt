package dev.typetype.server.portability

import java.io.OutputStream
import java.util.zip.ZipFile

class YoutubeTakeoutPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format = PortabilityFormat.YOUTUBE_TAKEOUT,
        adapterVersion = 1,
        capabilities = TAKEOUT_CATEGORIES.mapTo(linkedSetOf()) { category ->
            PortabilityCapability(category, setOf(PortabilityDirection.IMPORT), PortabilityFidelity.COMPLETE)
        },
        defaultExtension = "zip",
        contentType = "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        val archive = input.archive ?: return null
        val names = archive.names.map(String::lowercase)
        val youtubeFiles = names.count { name ->
            "youtube" in name && (name.endsWith(".csv") || name.endsWith(".html"))
        }
        if (youtubeFiles == 0) return null
        val hasTakeoutRoot = names.any { it.startsWith("takeout/") }
        return PortabilityDetection(
            PortabilityFormat.YOUTUBE_TAKEOUT,
            null,
            if (hasTakeoutRoot) 99 else 90,
            "YouTube Takeout CSV or activity files",
        )
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        requireNotNull(detect(input)) { "Unsupported YouTube Takeout archive" }
        ZipFile(input.path.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            YoutubeTakeoutCsvPortabilityReader.readManifests(zip, entries, sink)
            YoutubeTakeoutHtmlPortabilityReader.read(zip, entries, sink)
            YoutubeTakeoutCsvPortabilityReader.readContent(zip, entries, sink)
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ): Unit = error("YouTube Takeout export is not available")
}

private val TAKEOUT_CATEGORIES = setOf(
    PortabilityCategory.SUBSCRIPTIONS,
    PortabilityCategory.HISTORY,
    PortabilityCategory.PLAYLISTS,
    PortabilityCategory.WATCH_LATER,
    PortabilityCategory.FAVORITES,
)
