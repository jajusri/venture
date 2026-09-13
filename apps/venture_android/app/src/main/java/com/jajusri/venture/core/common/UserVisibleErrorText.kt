package com.jajusri.venture.core.common

/**
 * Maps failures to user-visible copy. Diagnostic [Throwable] details stay on [AppError.cause]
 * (and logs); they must not appear in presentation state.
 */
object UserVisibleErrorText {
    const val UNEXPECTED = "Something went wrong. Please try again."
    const val OFFLINE = "Device is offline."
    const val TIMEOUT = "The request timed out."
    const val CONNECTOR_UNAVAILABLE = "The Connector could not complete this request."
    const val NOT_ALLOWED = "This action is not allowed right now."
    const val NOT_FOUND = "That item could not be found."
    const val UNREADABLE = "The Connector returned data that could not be read."
    const val NETWORK = "A network error occurred. Check the Connector connection."

    fun fromAppError(error: AppError): String = when (error) {
        is AppError.Offline -> OFFLINE
        is AppError.Timeout -> TIMEOUT
        is AppError.Remote -> fromRemote(error.httpStatus, error.message)
        is AppError.Serialization -> sanitizeOr(error.message, UNREADABLE)
        is AppError.Message -> sanitizeOr(error.message, UNEXPECTED)
        is AppError.Unexpected -> fromThrowable(error.cause)
    }

    fun fromThrowable(@Suppress("UNUSED_PARAMETER") cause: Throwable?): String = UNEXPECTED

    fun fromRemote(httpStatus: Int?, message: String): String {
        val sanitized = if (looksTechnical(message)) null else message.trim().takeIf { it.isNotEmpty() }
        return when (httpStatus) {
            401, 403 -> sanitized ?: NOT_ALLOWED
            404 -> sanitized ?: NOT_FOUND
            in 500..599 -> sanitized ?: CONNECTOR_UNAVAILABLE
            else -> sanitized ?: CONNECTOR_UNAVAILABLE
        }
    }

    fun sanitizeOr(message: String, fallback: String): String {
        val trimmed = message.trim()
        if (trimmed.isEmpty() || looksTechnical(trimmed)) return fallback
        return trimmed
    }

    fun looksTechnical(message: String): Boolean {
        val lower = message.lowercase()
        return "exception" in lower ||
            "sqlite" in lower ||
            "constraint" in lower ||
            "android.database" in lower ||
            "caused by:" in lower ||
            "stacktrace" in lower ||
            "econnrefused" in lower ||
            "enotfound" in lower ||
            "cleartext" in lower ||
            "failed to connect" in lower ||
            "unknownhost" in lower ||
            ".kt:" in lower ||
            ".java:" in lower ||
            Regex("HTTP\\s+\\d{3}", RegexOption.IGNORE_CASE).containsMatchIn(message)
    }
}
