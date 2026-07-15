# Contributing to TypeType Server

Thank you for helping improve the TypeType backend.

## Scope

This repository owns the Kotlin HTTP API, extraction integration, playback-session backend, authentication, user data, imports, persistence, and service gateways.

Open bug reports and feature requests in the [central TypeType issue tracker](https://github.com/TypeType-Video/TypeType/issues). Mention that the change affects `TypeType-Server` and link the issue from your pull request.

Frontend work belongs in [TypeType-Frontend](https://github.com/TypeType-Video/TypeType-Frontend). Download execution belongs in [TypeType-Downloader](https://github.com/TypeType-Video/TypeType-Downloader). YouTube token and decoder behavior belongs in [TypeType-Token](https://github.com/TypeType-Video/TypeType-Token).

## Set up the project

Use JDK 25, Docker Engine, and Docker Compose v2.

```sh
git switch dev
cp .env.example .env
docker compose up -d postgres dragonfly
./gradlew shadowJar
java -jar build/libs/typetype-server-all.jar
```

The server starts at `http://localhost:8080` by default.

## Source layout

| Path | Responsibility |
| --- | --- |
| `src/main/kotlin/dev/typetype/server/routes` | HTTP route definitions |
| `src/main/kotlin/dev/typetype/server/services` | Application and extraction behavior |
| `src/main/kotlin/dev/typetype/server/models` | API and domain models |
| `src/main/kotlin/dev/typetype/server/db` | Persistence and database tables |
| `src/main/kotlin/dev/typetype/server/downloader` | Downloader gateway integration |
| `src/test` | Unit, route, integration, and regression tests |
| `openapi.yaml` and `openapi/` | Public API contract |

Keep routing, service behavior, extraction, and persistence in their owning modules.

## Extraction changes

PipePipe Client and PipePipeExtractor are the behavioral references for extraction and SABR work. Check their current source before changing request construction, selected formats, playback contexts, cookies, buffered ranges, reload handling, or segment completion.

When a defect is general to PipePipeExtractor, prefer contributing the correction upstream. Keep TypeType-specific behavior in this repository only when it belongs to the TypeType API or when the upstream API cannot express the required backend behavior cleanly.

## Kotlin expectations

- Use `val` unless mutation is required.
- Do not use `!!` or wildcard imports.
- Add explicit return types to public functions.
- Prefer sealed result types when callers must handle distinct outcomes.
- Keep production files under 200 lines and split by responsibility.
- Keep HTTP handlers thin and put behavior in services.
- Add focused tests for behavior changes and regressions.
- Update the OpenAPI files whenever a public request or response contract changes.
- Preserve the HTTP boundary between this GPL backend and the MIT frontend.

## Required checks

```sh
./gradlew test
./gradlew shadowJar
./gradlew validateOpenApi
```

For changes covered by the full Gradle lifecycle, also run:

```sh
./gradlew check
```

The build must complete without Kotlin compiler warnings.

## Commits and pull requests

Create your branch from `dev` and open the pull request against `dev`.

Use commit messages in this form:

```text
type: short description
```

Common types are `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, and `style`. Use the imperative mood and keep the first line under 72 characters.

Explain the behavioral change, affected endpoints, tests, and any required Token, Downloader, Player, or Frontend update in the pull request.

Contributions to this repository are distributed under [GPL-3.0](LICENSE).
