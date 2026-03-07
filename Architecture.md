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

- Language: Kotlin
- Toolchain: Gradle, Ktor, PipePipeExtractor
- License: GPL v3
- No TypeScript or JavaScript code enters this repository

## Boundary

The REST API is the only point of contact between the two repositories. No code, no types, no logic crosses this boundary. Types consumed by the frontend are defined in its own `packages/types` workspace and derived from API contracts — not from Kotlin classes.

## Reference Material

- `InfinityLoop1308/PipePipeExtractor` — the extraction engine this server wraps
- `InfinityLoop1308/PipePipeClient` — reference for how PipePipeExtractor is consumed in Java
- `InfinityLoop1308/PipePipe` — multi-service support reference
- `TeamPiped/Piped` — API patterns reference
