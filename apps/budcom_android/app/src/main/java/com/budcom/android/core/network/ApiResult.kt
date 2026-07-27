package com.budcom.android.core.network

/**
 * Result of a single Connector HTTP call at the network boundary.
 *
 * Repositories map [ApiResult] into domain [com.budcom.android.core.common.AppResult].
 * Prefer this over throwing across repository/data-source boundaries.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val error: NetworkError) : ApiResult<Nothing>()
}

/** Returns [data] on success, or `null` on failure. */
fun <T> ApiResult<T>.getOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}

/** Returns the [NetworkError] on failure, or `null` on success. */
fun <T> ApiResult<T>.errorOrNull(): NetworkError? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Failure -> error
}

/** Maps success values; failures pass through unchanged. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

/** Flat-maps success values; failures pass through unchanged. */
inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Failure -> this
}
