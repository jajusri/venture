package com.budcom.android.core.network

/**
 * Exception form of a [NetworkError] for call sites that must use throwable control flow
 * (e.g. bridging third-party APIs). Prefer [ApiResult] at repository boundaries.
 */
class ApiException(
    val error: NetworkError,
) : Exception(error.toExceptionMessage(), error.causeOrNull()) {

    companion object {
        fun of(error: NetworkError): ApiException = ApiException(error)
    }
}

private fun NetworkError.toExceptionMessage(): String = when (this) {
    is NetworkError.NoConnectivity -> "No network connectivity."
    is NetworkError.Timeout -> message
    is NetworkError.Http -> buildString {
        append("HTTP ")
        append(httpStatus)
        if (!code.isNullOrBlank()) {
            append(" [")
            append(code)
            append(']')
        }
        append(": ")
        append(message)
    }
    is NetworkError.Serialization -> message
    is NetworkError.Unknown -> message
}

private fun NetworkError.causeOrNull(): Throwable? = when (this) {
    is NetworkError.Timeout -> cause
    is NetworkError.Serialization -> cause
    is NetworkError.Unknown -> cause
    is NetworkError.NoConnectivity,
    is NetworkError.Http,
    -> null
}
