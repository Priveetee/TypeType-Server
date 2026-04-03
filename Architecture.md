# Architecture

## Overview

TypeType-Server is the extraction and data backend for TypeType.

Responsibilities:

- expose extraction APIs for YouTube, NicoNico, and BiliBili via PipePipeExtractor
- persist user data in PostgreSQL

TypeType-Server is consumed only over HTTP by the TypeType frontend.

## Service Boundary

```
PipePipeExtractor (Java, GPL v3)
        |
        v
TypeType-Server (Kotlin/Ktor, GPL v3)
        |
        | REST/JSON
        v
TypeType frontend (TypeScript/React, MIT)
```

No code is shared across repos. The REST API is the boundary.

## Runtime Components

| Component | Role |
|---|---|
| Ktor (Netty) | HTTP server |
| PipePipeExtractor | media extraction |
| PostgreSQL | user data persistence |
| Dragonfly (Redis-compatible) | extraction cache |
| TypeType-Token | YouTube PO token and subtitles helper |

## Authentication and Authorization

Authentication is JWT-based.

- public auth routes:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `GET /auth/me`
  - `POST /auth/guest`
  - `POST /auth/reset-password`
- protected user routes require `Authorization: Bearer <jwt>`
- admin-only routes use admin role checks
- admin/moderator routes use admin or moderator role checks

Guest users can read protected resources but are blocked on selected write routes (for example bug report creation).

## Routing Layout

Main route registration lives in `src/main/kotlin/dev/typetype/server/Application.kt`.

- extraction routes:
  - `/streams`
  - `/streams/manifest`
  - `/streams/native-manifest`
  - `/search`
  - `/suggestions`
  - `/trending`
  - `/comments`
  - `/bullet-comments`
  - `/channel`
- proxy routes:
  - `/proxy`
  - `/proxy/storyboard`
  - `/proxy/nicovideo`
- user data routes:
  - `/history`
  - `/subscriptions`
  - `/subscriptions/feed`
  - `/subscriptions/shorts`
  - `/playlists`
  - `/watch-later`
  - `/progress`
  - `/favorites`
  - `/settings`
  - `/search-history`
  - `/blocked/channels`
  - `/blocked/videos`
  - `/recommendation-events`
  - `/recommendation-feedback`
  - `/restore`
  - `/recommendations/home`
  - `/bug-reports`
- admin routes:
  - `/admin/users`
  - `/admin/settings`
  - `/admin/bug-reports`

## Data Domains

Persisted user domains include:

- history
- subscriptions
- playlists and playlist videos
- favorites
- watch later
- playback progress
- settings
- search history
- blocked channels and videos
- recommendation feedback/events
- bug reports

Schema definitions are under `src/main/kotlin/dev/typetype/server/db/tables`.

## Rate Limiting

Rate limits are configured by zone in `configurePlugins()`.

- extraction zone for heavy extraction endpoints
- channel zone for channel extraction
- proxy zone for media proxy
- proxy storyboard zone for storyboard proxy
- user data zone for authenticated data routes

## Error Model

Errors return JSON using:

```json
{"error":"..."}
```

Common statuses:

- `400` invalid input
- `401` missing or invalid token
- `403` role/permission violation
- `404` resource not found
- `409` conflict
- `422` extraction/validation constraints

## License

This repository is GPL v3 due to PipePipeExtractor linkage.

- keep backend and frontend code separated by HTTP boundary
- ensure added dependencies remain GPL-compatible
