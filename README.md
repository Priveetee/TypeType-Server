# TypeType-Server

The extraction server for [TypeType](https://github.com/Priveetee/TypeType).

A Kotlin/Ktor wrapper around [PipePipeExtractor](https://github.com/InfinityLoop1308/PipePipeExtractor), exposing multi-service extraction for YouTube, NicoNico, and BiliBili as a REST/JSON API. Consumed exclusively by the TypeType frontend.

Read the [Manifesto](https://github.com/Priveetee/TypeType/blob/main/MANIFESTO.md) to understand the project and the architectural decisions behind this separation.

## Stack

| Role | Tool |
|---|---|
| Language | Kotlin |
| Server | Ktor |
| Extraction | PipePipeExtractor |
| Build | Gradle |

## License

GPL v3. This server links directly to PipePipeExtractor, which is GPL v3. The TypeType frontend is a separate program communicating over HTTP and is not subject to this license.
