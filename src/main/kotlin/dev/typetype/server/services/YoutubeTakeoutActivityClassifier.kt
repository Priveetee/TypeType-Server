package dev.typetype.server.services

object YoutubeTakeoutActivityClassifier {
    private val watchedPhrases = setOf(
        "You watched",
        "Vous avez regardé",
        "Has visto",
        "Você assistiu",
        "hai guardato",
        "İzlediniz",
        "du hast angesehen",
        "вы смотрели",
        "視聴しました",
        "시청한 동영상",
        "已观看",
        "已觀看",
    )

    private val likedPhrases = setOf(
        "You liked",
        "Liked",
        "Vous avez aimé",
        "A aimé",
        "te ha gustado",
        "Você gostou",
        "hai messo mi piace",
        "Beğendiniz",
        "gefällt mir",
        "понравилось",
        "高く評価しました",
        "좋아요 표시함",
        "点赞了",
        "按讚",
    )

    private val subscribedPhrases = setOf(
        "You subscribed to",
        "Vous vous êtes abonné à",
        "te has suscrito a",
        "Você se inscreveu em",
        "ti sei iscritto a",
        "Abone oldunuz",
        "du hast abonniert",
        "вы подписались на",
        "登録しました",
        "구독함",
        "已订阅",
        "已訂閱",
    )

    fun isWatched(value: String): Boolean = containsAny(value, watchedPhrases)

    fun isLiked(value: String): Boolean = containsAny(value, likedPhrases)

    fun isSubscribed(value: String): Boolean = containsAny(value, subscribedPhrases)

    val watchedPattern: String = pattern(watchedPhrases)

    val likedPattern: String = pattern(likedPhrases)

    val subscribedPattern: String = pattern(subscribedPhrases)

    private fun containsAny(value: String, phrases: Set<String>): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(value)
        return phrases.any { YoutubeTakeoutTextNormalizer.normalize(it) in normalized }
    }

    private fun pattern(phrases: Set<String>): String = phrases.joinToString("|") { Regex.escape(it) }
}
