package dev.typetype.server.models

sealed class ExtractionResult<out T> {
    data class Success<T>(val data: T) : ExtractionResult<T>()
    data class Failure(val message: String) : ExtractionResult<Nothing>()
}
