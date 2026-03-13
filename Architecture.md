# Architecture

## Overview

TypeType-Server is the extraction and user data backend for the TypeType platform. It is a Kotlin/Ktor server with two responsibilities:

- Expose extraction capabilities for YouTube, NicoNico, and BiliBili as a REST/JSON API via PipePipeExtractor
- Persist user data (history, subscriptions, playlists, favorites, watch-later, progress, settings, search history, blocked content) in PostgreSQL

## Layers

```
PipePipeExtractor  (Java, GPL v3)
        |
        | linked directly
        v
TypeType-Server  (Kotlin/Ktor, GPL v3)  <- this repository
        |
        | REST / JSON over HTTP
        v
TypeType  (TypeScript + React, MIT)
```

### PipePipeExtractor

Java library maintained by InfinityLoop1308. Supports YouTube, NicoNico, BiliBili. Handles extraction, parsing, and format resolution. This is the extraction engine — it is not modified, only consumed.

Repository: `https://github.com/InfinityLoop1308/PipePipeExtractor`

### TypeType-Server (this repository)

A Kotlin/Ktor server that wraps PipePipeExtractor for extraction and persists user data in PostgreSQL. Extraction results are cached in Dragonfly (Redis-compatible). All user data endpoints require an `X-Instance-Token` header.

Because it links directly to PipePipeExtractor (GPL v3), this server must be distributed under GPL v3.

### TypeType (separate repository)

The frontend — a TypeScript/React SPA. It consumes this server's REST API over HTTP. It is a separate program and is not subject to GPL v3.

Repository: `https://github.com/Priveetee/TypeType`

## Server Stack

| Role | Tool | License |
|---|---|---|
| Language | Kotlin | Apache 2.0 |
| Server | Ktor (Netty engine) | Apache 2.0 |
| Extraction | PipePipeExtractor | GPL v3 |
| Build | Gradle (Kotlin DSL) | Apache 2.0 |
| User data | PostgreSQL via Exposed + HikariCP | Apache 2.0 |
| Cache | Dragonfly (Redis-compatible) | BSL 1.1 |

## License Constraints

- This server is GPL v3 — required by PipePipeExtractor, do not change.
- No frontend code (TypeScript, JavaScript, HTML, CSS) enters this repository.
- No server code enters the TypeType frontend repository.
- The REST API is the hard boundary between the two licenses.
- Before adding any dependency, verify it is compatible with GPL v3.

## Data Flow

```
HTTP request from TypeType frontend
        |
        v
Ktor route handler
        |
        +-- extraction endpoints --> Dragonfly cache (hit) --> JSON response
        |                        --> PipePipeExtractor ------> Dragonfly cache (store) --> JSON response
        |
        +-- user data endpoints  --> PostgreSQL (read/write) --> JSON response
```

## Repository Structure

```
TypeType-Server/
├── src/
│   └── main/kotlin/dev/typetype/server/
│       ├── routes/      (Ktor route definitions, one file per resource)
│       ├── services/    (extraction logic, PipePipeExtractor calls, user data services)
│       ├── models/      (response data classes)
│       ├── db/          (PostgreSQL schema and table definitions via Exposed)
│       ├── cache/       (Dragonfly/Redis client and caching wrappers)
│       └── downloader/  (OkHttp-based NewPipe downloader implementation)
└── build.gradle.kts
```

## Boundary

The REST API is the only point of contact between the two repositories. No code, no types, no logic crosses this boundary. Types consumed by the frontend are defined in its own `packages/types` workspace and derived from API contracts — not from Kotlin classes.

## Rate Limiting

Three zones are enforced per IP per minute:

| Zone | Limit | Applies to |
|---|---|---|
| `extraction` | 60 req/min | `/streams`, `/streams/manifest`, `/streams/native-manifest`, `/search`, `/suggestions`, `/trending`, `/comments`, `/bullet-comments`, `/channel` |
| `proxy` | 300 req/min | `/proxy`, `/proxy/nicovideo` |
| `user-data` | 120 req/min | all protected user data endpoints |

## Authentication

Protected endpoints require the `X-Instance-Token` header. The token is generated on first use and persisted in PostgreSQL. Obtain it via `GET /token`. All requests missing or sending a wrong token receive HTTP 401.

## CORS

Allowed origins are configured via the `ALLOWED_ORIGINS` environment variable (comma-separated). The server refuses to start if this variable is not set.

## API Contract

All endpoints respond with JSON. Errors return `{ "error": "<message>" }`.

The `service` query parameter is an integer:

| Value | Platform |
|---|---|
| `0` | YouTube |
| `3` | SoundCloud |
| `4` | media.ccc.de |
| `5` | BiliBili |
| `6` | NicoNico |

---

### Public Endpoints

#### `GET /streams?url={fullVideoUrl}`

Full video URL required (e.g. `https://www.youtube.com/watch?v=...`).

```
StreamResponse
  id                        string
  title                     string
  uploaderName              string
  uploaderUrl               string
  uploaderAvatarUrl         string
  thumbnailUrl              string
  description               string
  duration                  number        (seconds)
  viewCount                 number
  likeCount                 number
  dislikeCount              number        (-1 if unavailable)
  uploadDate                string        (textual, e.g. "3 days ago" — not ISO 8601)
  uploaded                  number        (epoch ms — -1 when unavailable)
  uploaderSubscriberCount   number        (-1 if unavailable)
  uploaderVerified          boolean
  category                  string
  license                   string
  visibility                string
  tags                      string[]
  streamType                string
  isShortFormContent        boolean
  requiresMembership        boolean
  startPosition             number        (seconds)
  streamSegments            StreamSegmentItem[]
  hlsUrl                    string        (empty string if unavailable)
  dashMpdUrl                string        (empty string if unavailable)
  videoStreams               VideoStreamItem[]   (muxed — video + audio)
  audioStreams               AudioStreamItem[]   (audio only)
  videoOnlyStreams           VideoStreamItem[]   (video only — no audio track)
  subtitles                 SubtitleItem[]
  previewFrames             PreviewFrameItem[]
  sponsorBlockSegments      SponsorBlockSegmentItem[]
  relatedStreams            VideoItem[]

VideoStreamItem
  url             string
  mimeType        string
  format          string        ("MPEG_4" | "WEBM" | "v3GPP")
  resolution      string        ("1080p", "720p", ...)
  bitrate         number | null (null if unavailable)
  codec           string | null (null if unavailable)
  isVideoOnly     boolean
  itag            number
  width           number
  height          number
  fps             number
  contentLength   number
  initStart       number
  initEnd         number
  indexStart      number
  indexEnd        number

AudioStreamItem
  url               string
  mimeType          string
  format            string
  bitrate           number | null (null if unavailable)
  codec             string | null (null if unavailable)
  quality           string | null (null if unavailable)
  itag              number
  contentLength     number
  initStart         number
  initEnd           number
  indexStart        number
  indexEnd          number
  audioTrackId      string | null
  audioTrackName    string | null
  audioLocale       string | null

SubtitleItem
  url                   string
  mimeType              string
  languageTag           string
  displayLanguageName   string
  isAutoGenerated       boolean

StreamSegmentItem
  title             string
  startTimeSeconds  number
  channelName       string | null
  url               string | null
  previewUrl        string | null

PreviewFrameItem
  urls              string[]
  frameWidth        number
  frameHeight       number
  totalCount        number
  durationPerFrame  number
  framesPerPageX    number
  framesPerPageY    number

SponsorBlockSegmentItem
  startTime   number  (seconds, Double)
  endTime     number  (seconds, Double)
  category    string  (e.g. "sponsor", "intro", "outro")
  action      string  (e.g. "skip", "mute")
```

#### `GET /streams/manifest?url={fullVideoUrl}`

Returns a DASH MPD generated from PipePipeExtractor streams. Response content type is `application/dash+xml`.

NicoNico returns HTTP 422 — no separate video/audio streams exist, DASH generation is not possible.

#### `GET /streams/native-manifest?url={fullVideoUrl}`

Returns the YouTube native DASH manifest with all stream URLs rewritten through `/proxy`. Response content type is `application/dash+xml`.

#### `GET /search?q={query}&service={service}&nextpage={cursor}`

`nextpage` is optional. Omit on the first request. Pass the value from the previous response to fetch the next page. An invalid cursor returns HTTP 400.

```
SearchPageResponse
  items               VideoItem[]
  nextpage            string | null   (null when no further pages are available)
  searchSuggestion    string | null
  isCorrectedSearch   boolean
```

#### `GET /suggestions?query={query}&service={service}`

Returns `string[]` — a list of autocomplete suggestion strings.

Note: the parameter name is `query` (not `q`).

#### `GET /trending?service={service}`

Returns `VideoItem[]`.

```
VideoItem
  id                  string        (full video URL)
  title               string
  url                 string        (full video URL — same as id)
  thumbnailUrl        string
  uploaderName        string
  uploaderUrl         string        (empty string if unavailable)
  uploaderAvatarUrl   string        (empty string if unavailable)
  duration            number        (seconds)
  viewCount           number
  uploadDate          string        (textual — empty string if unavailable)
  uploaded            number        (epoch ms — -1 when unavailable)
  streamType          string
  isShortFormContent  boolean
  uploaderVerified    boolean
  shortDescription    string | null
```

#### `GET /comments?url={fullVideoUrl}&nextpage={cursor}`

`nextpage` is optional. Omit on the first request.

If the platform has no comment extractor, the response is `{ "comments": [], "nextpage": null, "commentsDisabled": false }` — not an error.

```
CommentsPageResponse
  comments          CommentItem[]
  nextpage          string | null
  commentsDisabled  boolean

CommentItem
  id                    string
  text                  string
  author                string
  authorUrl             string
  authorAvatarUrl       string
  likeCount             number        (-1 if unavailable)
  textualLikeCount      string        (empty string if unavailable)
  publishedTime         string        (textual — empty string if unavailable)
  isHeartedByUploader   boolean
  isPinned              boolean
  uploaderVerified      boolean
  replyCount            number        (-1 if unavailable)
  repliesPage           string | null (cursor to fetch replies — null if none)
```

#### `GET /comments/replies?url={fullVideoUrl}&repliesPage={cursor}`

Fetches replies for a comment. `repliesPage` is the cursor from `CommentItem.repliesPage`. Returns `CommentsPageResponse`.

#### `GET /bullet-comments?url={fullVideoUrl}&nextpage={cursor}`

NicoNico and BiliBili danmaku comments. `nextpage` is optional.

```
BulletCommentsPageResponse
  comments    BulletCommentItem[]
  nextpage    string | null

BulletCommentItem
  text              string
  argbColor         number
  position          string
  relativeFontSize  number
  durationMs        number
  isLive            boolean
```

#### `GET /channel?url={channelUrl}&nextpage={cursor}`

`nextpage` is optional. Metadata fields (`name`, `description`, `avatarUrl`, `bannerUrl`, `subscriberCount`, `isVerified`) are only populated on the first page. Subsequent pages return empty values for these fields — the frontend is expected to retain metadata from the first page.

```
ChannelResponse
  name              string
  description       string
  avatarUrl         string
  bannerUrl         string
  subscriberCount   number        (-1 if unavailable)
  isVerified        boolean
  videos            VideoItem[]
  nextpage          string | null
```

#### `GET /proxy?url={mediaUrl}`

Upstream media proxy. Forwards the `Range` header. Passes through `Content-Range` and `Accept-Ranges` response headers. Strips `cpn` and `pppid` CMCD query parameters before forwarding. Injects `Referer: https://www.bilibili.com` for BiliBili CDN URLs. Blocks SSRF (private IP ranges and localhost are rejected).

#### `GET /proxy/nicovideo?url={mediaUrl}&domand_bid={bid}`

NicoNico-specific proxy. Handles `.m3u8` manifest requests and segment requests. `domand_bid` is optional.

#### `GET /token`

Returns the instance token. Creates it on first call and persists it.

```
{ "token": string }
```

---

### Protected Endpoints

All protected endpoints require the `X-Instance-Token` header. Missing or invalid tokens return HTTP 401.

#### History — `/history`

| Method | Path | Description |
|---|---|---|
| GET | `/history` | List entries. Query params: `q` (search), `from`/`to` (epoch ms range), `limit` (1–1000, default 60), `offset` (default 0). Response includes `X-Total-Count` header. |
| POST | `/history` | Add entry. Body: `HistoryItem`. Returns 201 with created item. |
| DELETE | `/history/{id}` | Delete one entry by id. Returns 204 or 404. |
| DELETE | `/history` | Delete all entries. Returns 204. |

```
HistoryItem
  id            string    (generated on create)
  url           string
  title         string
  thumbnail     string
  channelName   string
  channelUrl    string
  channelAvatar string
  duration      number    (seconds)
  progress      number    (seconds)
  watchedAt     number    (epoch ms)
```

#### Subscriptions — `/subscriptions`

| Method | Path | Description |
|---|---|---|
| GET | `/subscriptions` | List all subscriptions. |
| POST | `/subscriptions` | Subscribe. Body: `SubscriptionItem`. Returns 201. |
| DELETE | `/subscriptions/{channelUrl}` | Unsubscribe. Returns 204 or 404. |

```
SubscriptionItem
  channelUrl    string
  name          string
  avatarUrl     string
  subscribedAt  number    (epoch ms)
```

#### Subscription Feed — `/subscriptions/feed`

| Method | Path | Description |
|---|---|---|
| GET | `/subscriptions/feed` | Paginated feed from all subscriptions. Query params: `page` (default 0), `limit` (1–100, default 30). Returns `SubscriptionFeedResponse`. |

```
SubscriptionFeedResponse
  videos    VideoItem[]
  nextpage  string | null
```

#### Playlists — `/playlists`

| Method | Path | Description |
|---|---|---|
| GET | `/playlists` | List all playlists (without videos). |
| POST | `/playlists` | Create playlist. Body: `PlaylistItem`. Returns 201. |
| GET | `/playlists/{id}` | Get playlist with videos. Returns 404 if not found. |
| PUT | `/playlists/{id}` | Update playlist metadata. Returns 204 or 404. |
| DELETE | `/playlists/{id}` | Delete playlist. Returns 204 or 404. |
| POST | `/playlists/{id}/videos` | Add video to playlist. Body: `PlaylistVideoItem`. Returns 201. |
| DELETE | `/playlists/{id}/videos/{videoUrl}` | Remove video from playlist. Returns 204 or 404. |

```
PlaylistItem
  id            string    (generated on create)
  name          string
  description   string
  videos        PlaylistVideoItem[]
  createdAt     number    (epoch ms)

PlaylistVideoItem
  id        string    (generated on create)
  url       string
  title     string
  thumbnail string
  duration  number    (seconds)
  position  number    (0-indexed order)
```

#### Favorites — `/favorites`

| Method | Path | Description |
|---|---|---|
| GET | `/favorites` | List all favorites. Returns `FavoriteItem[]`. |
| POST | `/favorites/{videoUrl}` | Add to favorites. Returns 201 with `FavoriteItem`. |
| DELETE | `/favorites/{videoUrl}` | Remove from favorites. Returns 204 or 404. |

```
FavoriteItem
  videoUrl      string
  favoritedAt   number    (epoch ms)
```

#### Watch Later — `/watch-later`

| Method | Path | Description |
|---|---|---|
| GET | `/watch-later` | List all entries. Returns `WatchLaterItem[]`. |
| POST | `/watch-later` | Add entry. Body: `WatchLaterItem`. Returns 201. |
| DELETE | `/watch-later/{videoUrl}` | Remove entry. Returns 204 or 404. |

```
WatchLaterItem
  url       string
  title     string
  thumbnail string
  duration  number    (seconds)
  addedAt   number    (epoch ms)
```

#### Progress — `/progress`

| Method | Path | Description |
|---|---|---|
| GET | `/progress/{videoUrl}` | Get playback position. Returns 404 if no position recorded — treat as `position: 0`, not an error. |
| PUT | `/progress/{videoUrl}` | Upsert playback position. Body: `{ "position": number }`. Returns `ProgressItem`. |

```
ProgressItem
  videoUrl    string
  position    number    (seconds)
  updatedAt   number    (epoch ms)
```

#### Settings — `/settings`

| Method | Path | Description |
|---|---|---|
| GET | `/settings` | Get settings. Returns defaults if never set. |
| PUT | `/settings` | Upsert settings. Body: `SettingsItem`. Returns updated `SettingsItem`. |

```
SettingsItem
  defaultService          number    (service id, default 0)
  defaultQuality          string    (default "1080p")
  autoplay                boolean   (default true)
  volume                  number    (0.0–1.0, default 1.0)
  muted                   boolean   (default false)
  subtitlesEnabled        boolean   (default false)
  defaultSubtitleLanguage string    (default "")
  defaultAudioLanguage    string    (default "")
```

#### Search History — `/search-history`

| Method | Path | Description |
|---|---|---|
| GET | `/search-history` | List all entries. Returns `SearchHistoryItem[]`. |
| POST | `/search-history` | Add entry. Body: `{ "term": string }`. Returns 201 with `SearchHistoryItem`. |
| DELETE | `/search-history` | Delete all entries. Returns 204. |

```
SearchHistoryItem
  id          string    (generated on create)
  term        string
  searchedAt  number    (epoch ms)
```

#### Blocked Channels — `/blocked/channels`

| Method | Path | Description |
|---|---|---|
| GET | `/blocked/channels` | List all blocked channels. Returns `BlockedItem[]`. |
| POST | `/blocked/channels` | Block a channel. Body: `BlockedItem`. Returns 201. |
| DELETE | `/blocked/channels/{channelUrl}` | Unblock. Returns 204 or 404. |

#### Blocked Videos — `/blocked/videos`

| Method | Path | Description |
|---|---|---|
| GET | `/blocked/videos` | List all blocked videos. Returns `BlockedItem[]`. |
| POST | `/blocked/videos` | Block a video. Body: `BlockedItem`. Returns 201. |
| DELETE | `/blocked/videos/{videoUrl}` | Unblock. Returns 204 or 404. |

```
BlockedItem
  url           string
  name          string | null
  thumbnailUrl  string | null
  blockedAt     number    (epoch ms)
```

---

## Reference Material

A huge thanks to all of these projects and their contributors. TypeType-Server is a wrapper, and none of it would exist without the hard work these teams put in first.

- `InfinityLoop1308/PipePipeExtractor` — the extraction engine at the core of this server. We wrap it, they built it.
- `InfinityLoop1308/PipePipeClient` — invaluable reference for understanding how to consume PipePipeExtractor correctly in Java
- `InfinityLoop1308/PipePipe` — reference for multi-service support and how a real client handles the edge cases
- `TeamPiped/Piped` — the API patterns and architecture that inspired a lot of the design decisions here
- `deniscerri/ytdlnis` — groundbreaking work on YouTube PO token integration that directly shaped the design of TypeType-Token
