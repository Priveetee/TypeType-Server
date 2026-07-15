<div align="center">
  <img src="https://raw.githubusercontent.com/TypeType-Video/TypeType/main/assets/banner.svg" alt="TypeType" width="100%">
  <h1>TypeType-Server</h1>
  <p>Extraction and private user data backend for TypeType.</p>
</div>

<div align="center">

[![Backend](https://img.shields.io/badge/backend-Ktor-7f52ff)](https://ktor.io)
[![Language](https://img.shields.io/badge/language-Kotlin-7f52ff)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-GPL%20v3-blue)](LICENSE)

</div>

TypeType-Server is the HTTP API behind TypeType. It wraps PipePipeExtractor for media extraction and stores private user data in PostgreSQL. The frontend talks to this server over HTTP only.

## What this is

A Kotlin/Ktor backend for extraction, user data, and downloader gateway routes.

It provides stream metadata, search, trending, comments, channels, user libraries, settings, import flows, and `/downloader/*` proxying for the native downloader service.

## What this is not

- Not a frontend source tree.
- Not a standalone YouTube clone.
- Not a downloader. Download jobs are handled by TypeType-Downloader.
- Not affiliated with YouTube, NicoNico, BiliBili, Piped, Invidious, or NewPipe.

## Stack

| Role | Tool |
|---|---|
| Language | Kotlin |
| Server | Ktor with Netty |
| Extraction | PipePipeExtractor |
| Build | Gradle Kotlin DSL |
| User data | PostgreSQL via Exposed + HikariCP |
| Cache | Dragonfly |
| Downloader gateway | TypeType-Downloader over HTTP |
| Token service | TypeType-Token over HTTP |

## Services

| Area | Purpose |
|---|---|
| Extraction | Streams, manifests, search, suggestions, trending, comments, channels |
| User data | History, subscriptions, playlists, favorites, watch later, progress, settings |
| Imports | YouTube Takeout and PipePipe backup ingestion |
| Proxying | Media proxy, storyboard proxy, NicoNico video proxy, downloader gateway |
| Admin | Instance metadata, users, sessions, bug reports |

## Development

Start local dependencies:

```bash
cp .env.example .env
docker compose up -d postgres dragonfly
```

Build the server jar:

```bash
./gradlew shadowJar
```

Run it locally:

```bash
java -jar build/libs/typetype-server-all.jar
```

The server listens on `http://localhost:8080`.

## Dev mirror stack

Run the frontend, backend, downloader, token service, database, cache, and Garage mirror stack:

```bash
docker compose -f docker-compose.dev-mirror.yml up -d
./scripts/bootstrap-garage.sh
```

On `arm64`/`aarch64` hosts, add the Dragonfly ARM override:

```bash
docker compose -f docker-compose.dev-mirror.yml -f docker-compose.arm64.yml up -d
COMPOSE_OVERRIDE_FILE="$PWD/docker-compose.arm64.yml" ./scripts/bootstrap-garage.sh
```

| Service | URL |
|---|---|
| Frontend | `http://localhost:28082` |
| API server | `http://localhost:28080` |
| Downloader | `http://localhost:28093` |
| Token service | `http://localhost:28081` |

## Configuration

| Variable | Purpose |
|---|---|
| `ALLOWED_ORIGINS` | Required CORS origins |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USER` | PostgreSQL user |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `DRAGONFLY_URL` | Dragonfly connection URL |
| `DOWNLOADER_SERVICE_URL` | Base URL for TypeType-Downloader |
| `SUBTITLE_SERVICE_URL` | Base URL for TypeType-Token |

`DRAGONFLY_URL` is a Redis protocol URL consumed through Lettuce. The provided ARM Compose override keeps Dragonfly as the cache service, pins the `dragonfly` service to `linux/arm64`, and keeps the default URL as `redis://dragonfly:6379`.

## Checks

```bash
./gradlew test
./gradlew shadowJar
```

## Related projects

- [TypeType](https://github.com/TypeType-Video/TypeType) is the central stack and issue tracker.
- [TypeType-Frontend](https://github.com/TypeType-Video/TypeType-Frontend) is the React frontend.
- [TypeType-Downloader](https://github.com/TypeType-Video/TypeType-Downloader) handles download artifacts.
- [TypeType-Token](https://github.com/TypeType-Video/TypeType-Token) provides YouTube PO tokens.

## License

GPL v3. This license is required by PipePipeExtractor.
