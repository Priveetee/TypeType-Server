# Roadmap — PipePipeExtractor coverage gaps

Ne jamais commiter ce fichier.

Services supportes : YouTube, NicoNico, BiliBili uniquement.

Legende support par service : YT = YouTube, NN = NicoNico, BB = BiliBili
- plein  : implementé, valeur réelle
- defaut : non implementé, retourne la valeur par defaut de l'interface
- N/A    : concept inapplicable au service

---

## Phase 1 — Enrichissement des endpoints existants (aucune nouvelle route)

### 1.1 `StreamResponse` — champs manquants

| Champ | Type | Source extractor | YT | NN | BB | Defaut |
|---|---|---|---|---|---|---|
| `startPosition` | `Int` | `StreamInfo.getStartPosition()` | plein | defaut | defaut | `0` |
| `isShortFormContent` | `Boolean` | `StreamInfo.isShortFormContent()` | plein | defaut | defaut | `false` |
| `requiresMembership` | `Boolean` | `StreamInfo.requiresMembership()` | plein | defaut | defaut | `false` |
| `streamSegments` | `List<StreamSegmentItem>` | `StreamInfo.getStreamSegments()` | plein | defaut | defaut | `[]` |

`streamType` remplace `livestream: Boolean` — valeurs possibles :
`VIDEO_STREAM`, `LIVE_STREAM`, `AUDIO_STREAM`, `AUDIO_LIVE_STREAM`, `POST_LIVE_STREAM`, `NONE`

| Champ | YT | NN | BB |
|---|---|---|---|
| `streamType` | VIDEO_STREAM ou LIVE_STREAM | toujours VIDEO_STREAM | toujours VIDEO_STREAM |

Nouveau model `StreamSegmentItem` :
```kotlin
@Serializable
data class StreamSegmentItem(
    val title: String,
    val startTimeSeconds: Int,
    val previewUrl: String,
)
```

Attention : `livestream` est actuellement documente dans Backend.md et utilise par le frontend.
Remplacer par `streamType` est un breaking change — coordonner avec le frontend avant de deployer.

### 1.2 `VideoItem` — champs manquants

| Champ | Type | Source extractor | YT | NN | BB | Defaut |
|---|---|---|---|---|---|---|
| `streamType` | `String` | `StreamInfoItem.getStreamType().name()` | VIDEO ou LIVE | toujours VIDEO | toujours VIDEO | `"VIDEO_STREAM"` |
| `isShortFormContent` | `Boolean` | `StreamInfoItem.isShortFormContent()` | plein | defaut | defaut | `false` |
| `uploaderVerified` | `Boolean` | `StreamInfoItem.isUploaderVerified()` | plein | defaut | defaut | `false` |
| `shortDescription` | `String?` | `StreamInfoItem.getShortDescription()` | plein | defaut | defaut | `null` |

### 1.3 `AudioStreamItem` — champ manquant

| Champ | Type | Source extractor | YT | NN | BB |
|---|---|---|---|---|---|
| `audioLocale` | `String?` | `AudioStream.getAudioLocale()` | plein | defaut | defaut |

YouTube multi-audio tracks uniquement. Retourne `null` pour NN et BB.

### 1.4 `CommentItem` — champs manquants

| Champ | Type | Source extractor | YT | NN | BB | Defaut |
|---|---|---|---|---|---|---|
| `textualLikeCount` | `String` | `CommentsInfoItem.getTextualLikeCount()` | plein | plein | plein | `""` |
| `uploaderVerified` | `Boolean` | `CommentsInfoItem.isUploaderVerified()` | plein | defaut | defaut | `false` |

Note : `streamPosition` retire de la roadmap — aucun des 3 services l'implemente.

### 1.5 `CommentsPageResponse` — champ manquant

| Champ | Type | Source extractor | YT | NN | BB |
|---|---|---|---|---|---|
| `commentsDisabled` | `Boolean` | `CommentsInfo.isCommentsDisabled()` | plein | defaut | plein |

### 1.6 `SearchPageResponse` — champs manquants

| Champ | Type | Source extractor | YT | NN | BB | Defaut |
|---|---|---|---|---|---|---|
| `searchSuggestion` | `String?` | `SearchInfo.getSearchSuggestion()` | plein | defaut | defaut | `null` |
| `isCorrectedSearch` | `Boolean` | `SearchInfo.isCorrectedSearch()` | plein | defaut | defaut | `false` |

### 1.7 `GET /trending` — pagination et kiosks

Ajouter `nextpage: String?` a la reponse de `/trending`.
Ajouter un parametre optionnel `kiosk` (string) pour acceder aux kiosks non-default.

Valeurs par service :
- YouTube : `"Trending"` (default)
- NicoNico : `"Hot"` (default), `"New"`
- BiliBili : `"Featured"` (default)

---

## Phase 2 — Recherche mixte (channels + playlists dans les resultats)

Actuellement `/search` filtre et ne retourne que des `StreamInfoItem`.
PipePipeExtractor retourne aussi des `ChannelInfoItem` et `PlaylistInfoItem`.

### 2.1 Nouveaux models

```kotlin
@Serializable
data class ChannelInfoItem(
    val url: String,
    val name: String,
    val thumbnailUrl: String,
    val description: String,
    val subscriberCount: Long,
    val streamCount: Long,
)

@Serializable
data class PlaylistInfoItem(
    val url: String,
    val name: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val streamCount: Long,
    val playlistType: String,
)
```

### 2.2 `SearchPageResponse` modifie

```kotlin
@Serializable
data class SearchPageResponse(
    val streams: List<VideoItem>,
    val channels: List<ChannelInfoItem>,
    val playlists: List<PlaylistInfoItem>,
    val nextpage: String?,
    val searchSuggestion: String?,
    val isCorrectedSearch: Boolean,
)
```

Breaking change — coordonner avec le frontend.

---

## Phase 3 — Nouveaux endpoints d extraction

### 3.1 `GET /playlist?url=<playlistUrl>`

Expose `PlaylistInfo` de PipePipeExtractor.

Reponse :
```kotlin
@Serializable
data class PlaylistResponse(
    val name: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val uploaderUrl: String,
    val uploaderAvatarUrl: String,
    val streamCount: Long,
    val playlistType: String,
    val streams: List<VideoItem>,
    val nextpage: String?,
)
```

Parametre optionnel `nextpage` pour la pagination.

### 3.2 `GET /channel/tab?url=<tabUrl>`

Expose `ChannelTabInfo` — donne acces aux tabs Shorts, Live, Playlists d une chaine.

Le frontend recupere les tabs via `GET /channel` (le champ `tabs: List<ChannelTab>` a ajouter).
Chaque `ChannelTab` a un `name` et un `url` (le `ListLinkHandler` serialise).
Le frontend appelle ensuite `GET /channel/tab?url=<tabUrl>` pour fetcher le contenu.

Reponse : meme shape que `SearchPageResponse` (streams, playlists, channels selon le tab).

Nouveau champ a ajouter sur `ChannelResponse` :
```kotlin
val tabs: List<ChannelTab>

@Serializable
data class ChannelTab(
    val name: String,
    val url: String,
)
```

### 3.3 `GET /feed?channelUrl=<channelUrl>`

Expose `FeedInfo` — fetch RSS/Atom d une chaine, beaucoup plus rapide qu un scrape complet.

Critique pour implementer un vrai feed d abonnements sans surcharger YouTube.

Reponse : `List<VideoItem>` (les derniers uploads de la chaine).

---

## Phase 4 — Niche / service-specific

### 4.1 BulletComments — `GET /bullet-comments?url=<videoUrl>`

Expose `BulletCommentsInfo`.
Supporte : YouTube, NicoNico, BiliBili.

```kotlin
@Serializable
data class BulletCommentItem(
    val text: String,
    val argbColor: Int,
    val position: String,
    val relativeFontSize: Double,
    val durationSeconds: Double,
    val lastingTimeMs: Int,
    val isLive: Boolean,
)
```

### 4.2 Multi-resolution images (`List<Image>`)

Ajouter un model `ImageItem` :
```kotlin
@Serializable
data class ImageItem(
    val url: String,
    val width: Int,
    val height: Int,
    val resolutionLevel: String,
)
```

Remplacer progressivement les champs `*Url: String` par `*Images: List<ImageItem>`
sur `StreamResponse`, `ChannelResponse`, `CommentItem`.

Breaking change majeur — a faire en dernier.

### 4.3 BiliBili multi-part videos — `partitions` sur `StreamResponse`

`List<VideoItem>` des parties d une video multi-part BiliBili.

---

## Ordre de livraison recommande

```
1.5 commentsDisabled                 (fix mineur, 5 lignes)
1.4 CommentItem enrichi              (textualLikeCount, uploaderVerified)
1.1 streamSegments + startPosition   (valeur immediate pour le player, YT uniquement)
1.3 AudioStreamItem audioLocale      (trivial, YT uniquement)
1.2 VideoItem enrichi                (streamType, isShortFormContent, uploaderVerified, shortDescription)
1.1 isShortFormContent + requiresMembership sur StreamResponse
1.6 SearchPageResponse (suggestion)  (avant le mixte)
1.7 Trending pagination + kiosks
2   Recherche mixte                  (coordonner breaking change avec frontend)
1.1 streamType remplace livestream   (coordonner breaking change avec frontend)
3.3 GET /feed                        (debloquer les abonnements)
3.1 GET /playlist                    (nouvelle feature)
3.2 GET /channel/tab                 (nouvelle feature)
4.1 BulletComments                   (YT + NN + BB)
4.2 List<Image>                      (breaking change majeur, en dernier)
4.3 partitions                       (BiliBili)
```
