# TypeType-Server

The extraction and user data backend for [TypeType](https://github.com/Priveetee/TypeType).

A Kotlin/Ktor server with two responsibilities:
- Wrap [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) to expose YouTube, NicoNico, and BiliBili extraction as a REST/JSON API
- Persist user data (history, favorites, subscriptions, playlists, watch-later, progress, settings, search history, blocked content) in PostgreSQL

See [Architecture.md](./Architecture.md) for current architecture and API surface overview.

## Stack

| Role | Tool |
|---|---|
| Language | Kotlin |
| Server | Ktor (Netty engine) |
| Extraction | PipePipeExtractor (Java, GPL v3) |
| Build | Gradle (Kotlin DSL) |
| User data | PostgreSQL via Exposed + HikariCP |
| Cache | Dragonfly (Redis-compatible) |

## Development

### Prerequisites

- JDK 17+
- Docker and Docker Compose (for PostgreSQL and Dragonfly)

### Start dependencies

```bash
cp .env.example .env
docker compose up -d postgres dragonfly
```

### Start full dev mirror stack (frontend + server + downloader)

```bash
docker compose -f docker-compose.dev-mirror.yml up -d
./scripts/bootstrap-garage.sh
```

Stack endpoints:

- Frontend: `http://localhost:28082`
- API server: `http://localhost:28080`
- Downloader: `http://localhost:28093`
- Token service: `http://localhost:28081`

Pull latest beta images before restart:

```bash
./scripts/pull-dev-images.sh
docker compose -f docker-compose.dev-mirror.yml up -d --force-recreate
```

### Build

```bash
./gradlew shadowJar
```

### Run

```bash
java -jar build/libs/typetype-server-all.jar
```

The server starts on port `8080`. Logs go to stdout.

### Docker image tags (GHCR)

Container tags are published to GHCR with:

- stable image `${{ github.repository }}` on `main` and Git tags `v*`
- beta image `${{ github.repository }}-beta` on `dev`
- `sha-<short-sha>` on every build
- branch tags (`main` on stable image, `dev` on beta image)
- `latest` on default branch (stable image) and on `dev` (beta image)
- `beta` on `dev` (beta image)
- release tags when pushing Git tags like `v1.2.3` (`1.2.3` and `1.2`) on stable image

### Configuration

All configuration is via environment variables. Defaults in `.env.example` work for local development.

| Variable | Default | Description |
|---|---|---|
| `ALLOWED_ORIGINS` | — | Comma-separated CORS origins. **Required** — server refuses to start without it. |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/typetype` | PostgreSQL JDBC URL |
| `DATABASE_USER` | `typetype` | PostgreSQL user |
| `DATABASE_PASSWORD` | `typetype` | PostgreSQL password |
| `DRAGONFLY_URL` | `redis://localhost:6379` | Dragonfly/Redis URL |
| `DOWNLOADER_SERVICE_URL` | `http://typetype-downloader:18093` | Downloader backend base URL used by `/downloader/*` gateway |

## Acknowledgments

A huge thanks to the projects that made this possible. TypeType-Server is a wrapper, and none of it would exist without the work these teams put in first.

- [InfinityLoop1308/PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) — the extraction engine at the core of this server
- [InfinityLoop1308/PipePipeClient](https://github.com/InfinityLoop1308/PipePipeClient) — reference for consuming PipePipeExtractor in Java
- [InfinityLoop1308/PipePipe](https://github.com/InfinityLoop1308/PipePipe) — reference for multi-service support
- [A-EDev/Flow](https://github.com/A-EDev/Flow) — inspiration for discovery-first recommendation direction and iteration patterns
- [TeamPiped/Piped](https://github.com/TeamPiped/Piped) — API patterns and architecture reference
- [deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis) — groundbreaking work on YouTube PO token integration that directly shaped the design of TypeType-Token

## License

GPL v3 — required by PipePipeExtractor. The TypeType frontend is a separate program communicating over HTTP and is not subject to this license.
