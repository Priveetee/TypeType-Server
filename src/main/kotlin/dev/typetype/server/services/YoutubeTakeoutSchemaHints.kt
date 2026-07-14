package dev.typetype.server.services

object YoutubeTakeoutSchemaHints {
    private val channelIdRegex = Regex("""^(UC|HC)[A-Za-z0-9_-]{10,}$""")
    private val videoIdRegex = Regex("""^[A-Za-z0-9_-]{6,}$""")
    private val playlistIdRegex = Regex("""^(PL|UU|LL|RD|OLAK5uy_)[A-Za-z0-9_-]{6,}$""")
    private val channelUrlRegex = Regex("""youtube\.com/(channel/|@|c/|user/)""", RegexOption.IGNORE_CASE)
    private val titleWords = setOf(
        "title",
        "titles",
        "titre",
        "titres",
        "titulo",
        "titulos",
        "baslik",
        "baslık",
        "başlık",
        "basligi",
        "baslıgı",
        "başlığı",
        "adi",
        "adı",
        "nombre",
        "nome",
        "nom",
        "name",
        "names",
        "titel",
        "titolo",
    )
    private val playlistWords = setOf("playlist", "playlists", "oynatma listesi", "oynatma listeleri")
    private val channelWords = setOf("channel", "chaine", "canal", "kanal", "kanaal", "канал", "チャンネル", "채널", "频道", "頻道")

    fun isChannelIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "channel id" ||
            normalized == "id des chaines" ||
            normalized == "id de la chaine" ||
            (channelWords.any { it in normalized } && ("id" in words(normalized) || "kimligi" in words(normalized) || "kimliği" in words(normalized)))
    }

    fun isChannelUrlHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "channel url" ||
            normalized == "url des chaines" ||
            normalized == "url de la chaine" ||
            ("url" in words(normalized) && channelWords.any { it in normalized })
    }

    fun isChannelTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "titres des chaines" ||
            normalized == "titre de la chaine" ||
            titleWords.any { it in words(normalized) } &&
            (channelWords.any { it in normalized } || normalized in titleWords)
    }

    fun isPlaylistIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "playlist id" ||
            normalized == "id de la playlist" ||
            (isPlaylistText(normalized) && ("id" in words(normalized) || "kimligi" in words(normalized) || "kimliği" in words(normalized)))
    }

    fun isPlaylistTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return isPlaylistText(normalized) && titleWords.any { it in words(normalized) }
    }

    fun isVideoIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "video id" || normalized == "id video" ||
            ("video" in normalized && ("id" in words(normalized) || "kimligi" in words(normalized) || "kimliği" in words(normalized)))
    }

    fun isVideoTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return "video" in normalized && titleWords.any { it in words(normalized) }
    }

    fun isPlaylistItemAddedAtHeader(value: String): Boolean {
        val normalized = normalize(value)
        val parts = words(normalized)
        return ("added" in parts && "at" in parts) ||
            ("creation" in parts && "video" in parts) ||
            ("created" in parts && "at" in parts) ||
            ("timestamp" in parts && "playlist" in normalized)
    }

    fun isUrlHeader(value: String): Boolean = "url" in words(normalize(value))

    fun looksLikeChannelId(value: String): Boolean = channelIdRegex.matches(value.trim())

    fun looksLikeVideoId(value: String): Boolean {
        val trimmed = value.trim()
        return videoIdRegex.matches(trimmed) && !looksLikeChannelId(trimmed) && !looksLikePlaylistId(trimmed)
    }

    fun looksLikePlaylistId(value: String): Boolean = playlistIdRegex.matches(value.trim())

    fun containsChannelUrl(value: String): Boolean = channelUrlRegex.containsMatchIn(value)

    fun containsWatchUrl(value: String): Boolean {
        val normalized = value.lowercase()
        return "youtube.com/watch?v=" in normalized || "youtube.com/shorts/" in normalized || "youtu.be/" in normalized
    }

    fun normalize(value: String): String = YoutubeTakeoutTextNormalizer.normalize(value)

    fun isPlaylistText(value: String): Boolean {
        val normalized = normalize(value)
        return playlistWords.any { normalize(it) in normalized }
    }

    private fun words(value: String): Set<String> = value.split(' ').filter { it.isNotBlank() }.toSet()
}
