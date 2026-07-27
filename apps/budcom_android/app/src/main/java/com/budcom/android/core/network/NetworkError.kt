package com.budcom.android.core.network

/**
 * Typed failure produced by Connector HTTP calls.
 *
 * Aligns with connector error bodies `{ code, message, details? }` when an HTTP
 * response is available; otherwise classifies transport failures.
 */
sealed class NetworkError {

    /**
     * Device has no usable network route to the Connector host.
     */
    data object NoConnectivity : NetworkError()

    /**
     * Connect / read / write / call deadline exceeded.
     */
    data class Timeout(
        val message: String = "The request timed out.",
        val cause: Throwable? = null,
    ) : NetworkError()

    /**
     * Non-2xx HTTP response from the Connector.
     *
     * @property httpStatus HTTP status code
     * @property code Connector `code` field when the body was parseable
     * @property message Human-readable message (Connector `message` or fallback)
     * @property details Optional Connector `details` object as a string map of primitives
     */
    data class Http(
        val httpStatus: Int,
        val code: String?,
        val message: String,
        val details: Map<String, String> = emptyMap(),
    ) : NetworkError()

    /**
     * Response body could not be decoded with the expected serializer.
     */
    data class Serialization(
        val message: String = "Failed to parse the server response.",
        val cause: Throwable? = null,
    ) : NetworkError()

    /**
     * Catch-all for unexpected failures.
     */
    data class Unknown(
        val message: String = "An unexpected network error occurred.",
        val cause: Throwable? = null,
    ) : NetworkError()
}

/**
 * Whether this error is safe to retry for idempotent GET-style Connector calls.
 */
fun NetworkError.isRetryable(): Boolean = when (this) {
    is NetworkError.NoConnectivity -> true
    is NetworkError.Timeout -> true
    is NetworkError.Http -> httpStatus == 408 || httpStatus == 429 || httpStatus in 500..599
    is NetworkError.Serialization -> false
    is NetworkError.Unknown -> false
}
