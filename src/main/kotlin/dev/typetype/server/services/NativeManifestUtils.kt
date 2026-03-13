package dev.typetype.server.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val MANIFEST_GOOGLEVIDEO_REGEX = Regex("""https://[a-z0-9.\-]+\.googlevideo\.com/[^"<\s]+""")

internal fun rewriteManifestUrls(manifest: String): String =
    manifest.replace(MANIFEST_GOOGLEVIDEO_REGEX) { match ->
        val raw = match.value.replace("&amp;", "&")
        val withPlaceholders = raw
            .replace("\$Number\$", "TMPL_NUMBER")
            .replace("\$Bandwidth\$", "TMPL_BANDWIDTH")
            .replace("\$Time\$", "TMPL_TIME")
        val encoded = "/proxy?url=" + URLEncoder.encode(withPlaceholders, StandardCharsets.UTF_8)
        encoded
            .replace("TMPL_NUMBER", "\$Number\$")
            .replace("TMPL_BANDWIDTH", "\$Bandwidth\$")
            .replace("TMPL_TIME", "\$Time\$")
    }
