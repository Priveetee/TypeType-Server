# TypeType-Server

The extraction and user data server for [TypeType](https://github.com/Priveetee/TypeType).

A Kotlin/Ktor server with two responsibilities:
- Wrap [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor) to expose YouTube, NicoNico, and BiliBili extraction as a REST/JSON API
- Persist user data (history, favorites, subscriptions, playlists, watch-later, progress, settings, search history, blocked content) in PostgreSQL

Consumed exclusively by the TypeType frontend. Read the [Manifesto](https://github.com/Priveetee/TypeType/blob/main/MANIFESTO.md) to understand the project and the architectural decisions behind this separation.

## Stack

| Role | Tool |
|---|---|
| Language | Kotlin |
| Server | Ktor |
| Extraction | PipePipeExtractor |
| Build | Gradle |
| User data | PostgreSQL via Exposed + HikariCP |
| Cache | Dragonfly (Redis-compatible) |

## Deployment

Requirements: Docker and Docker Compose.

```bash
git clone https://github.com/Priveetee/TypeType-Server.git
cd TypeType-Server
cp .env.example .env
```

Edit `.env` and set at minimum `ALLOWED_ORIGINS` to the URL of your TypeType frontend. Then:

```bash
docker compose up -d
```

The server starts on port `8080`. Override with `PORT=<port>` in `.env`.

## Configuration

All configuration is via environment variables defined in `.env`:

| Variable | Required | Description |
|---|---|---|
| `ALLOWED_ORIGINS` | Yes | Comma-separated list of allowed CORS origins |
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL |
| `DATABASE_USER` | Yes | PostgreSQL user |
| `DATABASE_PASSWORD` | Yes | PostgreSQL password |
| `DRAGONFLY_URL` | Yes | Dragonfly/Redis URL (e.g. `redis://dragonfly:6379`) |
| `PORT` | No | Server port (default: `8080`) |

The server refuses to start if `ALLOWED_ORIGINS` is not set.

## License

GPL v3. This server links directly to PipePipeExtractor, which is GPL v3. The TypeType frontend is a separate program communicating over HTTP and is not subject to this license.
