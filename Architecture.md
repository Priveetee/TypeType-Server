# Architecture

## Overview

TypeType-Server is the backend for extraction and user data.

It does two things:

- exposes extraction APIs for YouTube, NicoNico, and BiliBili via PipePipeExtractor
- persists user data in PostgreSQL

The frontend consumes it only over HTTP.

## System Boundary

```
PipePipeExtractor (Java, GPL v3)
        |
        v
TypeType-Server (Kotlin/Ktor, GPL v3)
        |
        v
TypeType frontend (TypeScript/React, MIT)
```

No source code is shared across repos. The REST API is the only boundary.

## Runtime Components

| Component | Role |
|---|---|
| Ktor (Netty) | HTTP server |
| PipePipeExtractor | extraction engine |
| PostgreSQL | user data storage |
| Dragonfly | extraction cache |
| TypeType-Token | PO token and subtitles helper |

## Authentication Model

Authentication is JWT-based.

Public auth routes:

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /auth/me`
- `POST /auth/guest`
- `POST /auth/reset-password`

Protected routes require `Authorization: Bearer <jwt>`.

Role checks:

- admin-only: `withAdminAuth`
- admin/moderator: `withAdminModeratorAuth`

## Route Surface

Main registration is in `src/main/kotlin/dev/typetype/server/Application.kt`.

Public extraction and proxy routes:

- `/streams`, `/streams/manifest`, `/streams/native-manifest`
- `/search`, `/suggestions`, `/trending`
- `/comments`, `/bullet-comments`, `/channel`
- `/proxy`, `/proxy/storyboard`, `/proxy/nicovideo`

Protected user routes:

- `/history`, `/subscriptions`, `/subscriptions/feed`, `/subscriptions/shorts`
- `/playlists`, `/watch-later`, `/progress`, `/favorites`, `/settings`
- `/search-history`, `/blocked/channels`, `/blocked/videos`
- `/recommendation-events`, `/recommendation-feedback`, `/recommendations/home`
- `/restore`, `/bug-reports`

Admin routes: `/admin/users`, `/admin/settings`, `/admin/bug-reports`

## Data Domains

Persisted user data domains:

- history
- subscriptions
- playlists and playlist items
- favorites
- watch later
- progress
- settings
- search history
- blocked channels and blocked videos
- recommendation events and feedback
- bug reports

Schema files live in `src/main/kotlin/dev/typetype/server/db/tables`.

## Rate Limiting

Request limits are applied by zone in plugin configuration:

- extraction zone
- channel zone
- proxy zone
- proxy storyboard zone
- user data zone

## Error Shape

Errors return JSON with a single key: `{"error":"..."}`.

Common statuses: `400`, `401`, `403`, `404`, `409`, `422`.

## License

This repository is GPL v3 because it links to PipePipeExtractor.

Keep backend and frontend code separated by the HTTP boundary.
