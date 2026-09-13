package com.jajusri.venture.core.common

/**
 * Domain/data result wrapper used across repository boundaries.
 *
 * Prefer this over throwing across layers so callers can map loading/success/failure
 * UI states without relying on exception control flow.
 */
sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

/**
 * Typed application error for transport and mapping failures.
 *
 * Network-layer [com.jajusri.venture.core.network.NetworkError] values are mapped here
 * by [com.jajusri.venture.core.network.ErrorMapper] so domain code stays transport-agnostic.
 */
sealed class AppError {
    /** No usable device network route. */
    data class Offline(val cause: Throwable? = null) : AppError()

    /** Request deadline exceeded. */
    data class Timeout(val cause: Throwable? = null) : AppError()

    /**
     * Remote Connector failure.
     *
     * @property httpStatus HTTP status when known
     * @property code Connector error `code` when present
     * @property message Safe display / log message
     */
    data class Remote(
        val httpStatus: Int?,
        val code: String?,
        val message: String,
        val cause: Throwable? = null,
    ) : AppError()

    data class Message(val message: String, val cause: Throwable? = null) : AppError()
    data class Serialization(val message: String, val cause: Throwable? = null) : AppError()
    data class Unexpected(val cause: Throwable) : AppError()
}

/**
 * Returns [value] on success, or `null` on failure.
 */
fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> null
}

/**
 * Returns the failure, or `null` on success.
 */
fun <T> AppResult<T>.errorOrNull(): AppError? = when (this) {
    is AppResult.Success -> null
    is AppResult.Failure -> error
}
