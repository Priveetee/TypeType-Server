# Architecture

## Overview

TypeType-Server is the extraction layer of the TypeType platform. It is a stateless Kotlin/Ktor server that wraps PipePipeExtractor and exposes its capabilities as a REST/JSON API. It contains no business logic, no user state, and nothing unrelated to extraction.

## Layers

```
PipePipeExtractor  (Java, GPL v3)
        |
        | linked directly
        v
TypeType-Server  (Kotlin/Ktor, GPL v3)  ← this repository
        |
        | REST / JSON over HTTP
        v
TypeType  (TypeScript + React, MIT)
```

### PipePipeExtractor

Java library maintained by InfinityLoop1308. Supports YouTube, NicoNico, BiliBili. Handles extraction, parsing, and format resolution. This is the engine — it is not modified, only consumed.

Repository: `https://github.com/InfinityLoop1308/PipePipeExtractor`

### TypeType-Server (this repository)

A thin wrapper around PipePipeExtractor. Its only responsibility is to expose extraction capabilities as a REST/JSON API. It does not contain business logic, user data management, or anything unrelated to extraction.

Because it links directly to PipePipeExtractor (GPL v3), this server must be distributed under GPL v3.

### TypeType (separate repository)

The frontend — a TypeScript/React SPA. It consumes this server's REST API over HTTP. It is a separate program and is not subject to GPL v3.

Repository: `https://github.com/Priveetee/TypeType`

## Server Stack

| Role | Tool | License |
|---|---|---|
| Language | Kotlin | Apache 2.0 |
| Server | Ktor | Apache 2.0 |
| Extraction | PipePipeExtractor | GPL v3 |
| Build | Gradle (Kotlin DSL) | Apache 2.0 |

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
        v
PipePipeExtractor (extraction)
        |
        v
JSON response
        |
        v
TypeType frontend
```

## Repository Structure

```
TypeType-Server/
├── src/
│   └── main/kotlin/dev/typetype/server/
│       ├── routes/      (Ktor route definitions)
│       ├── services/    (extraction logic, PipePipeExtractor calls)
│       └── models/      (response data classes)
└── build.gradle.kts
```

## Boundary

The REST API is the only point of contact between the two repositories. No code, no types, no logic crosses this boundary. Types consumed by the frontend are defined in its own `packages/types` workspace and derived from API contracts — not from Kotlin classes.

## API Contract

All endpoints respond with JSON. Errors return `{ "error": "<message>" }`.

Service identifiers accepted by `service` query parameter: `YouTube`, `NicoNico`, `BiliBili`.

### `GET /streams?url={fullVideoUrl}`

Full video URL required (e.g. `https://www.youtube.com/watch?v=...`).

```
StreamResponse
  id                      string
  title                   string
  uploaderName            string
  uploaderUrl             string
  thumbnailUrl            string
  description             string
  duration                number        (seconds)
  viewCount               number
  likeCount               number
  dislikeCount            number        (-1 if unavailable)
  uploadDate              string        (textual, e.g. "3 days ago" — not ISO 8601)
  hlsUrl                  string        (empty string if unavailable)
  dashMpdUrl              string        (empty string if unavailable)
  videoStreams            VideoStreamItem[]   (muxed — video + audio)
  audioStreams            AudioStreamItem[]   (audio only)
  videoOnlyStreams        VideoStreamItem[]   (video only — no audio track)
  sponsorBlockSegments    SponsorBlockSegmentItem[]

VideoStreamItem
  url           string
  format        string        ("MPEG_4" | "WEBM" | "v3GPP")
  resolution    string        ("1080p", "720p", …)
  bitrate       number | null (null if unavailable)
  codec         string        (e.g. "vp9", "avc1", "av01" — empty string if unavailable)
  isVideoOnly   boolean

AudioStreamItem
  url           string
  format        string
  bitrate       number | null (null if unavailable)
  codec         string        (e.g. "opus", "mp4a" — empty string if unavailable)
  quality       string | null (e.g. "medium" — null if unavailable)

SponsorBlockSegmentItem
  startTime     number        (milliseconds)
  endTime       number        (milliseconds)
  category      string        (e.g. "sponsor", "intro", "outro")
  action        string        (e.g. "skip", "mute")
```

### `GET /search?q={query}&service={service}`

### `GET /trending?service={service}`

Both return `VideoItem[]`:

```
VideoItem
  id                  string        (full video URL)
  title               string
  url                 string        (full video URL)
  thumbnailUrl        string
  uploaderName        string
  uploaderUrl         string        (channel page URL — empty string if unavailable)
  uploaderAvatarUrl   string        (uploader avatar image URL — empty string if unavailable)
  duration            number        (seconds)
  viewCount           number
  uploadDate          string        (textual — empty string if unavailable)
```

## Reference Material

- `InfinityLoop1308/PipePipeExtractor` — the extraction engine this server wraps
- `InfinityLoop1308/PipePipeClient` — reference for how PipePipeExtractor is consumed in Java
- `InfinityLoop1308/PipePipe` — multi-service support reference
- `TeamPiped/Piped` — API patterns reference
