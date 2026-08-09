package com.budcom.android.feature.serverconfig.domain.validation

/**
 * Validates and normalizes BudCom Connector base URLs for Retrofit/OkHttp.
 *
 * Rules (confirmed for this milestone):
 * - http or https only
 * - absolute URL with a non-empty host
 * - path must be empty or `/` (origin-only; dynamic interceptor rewrites host only)
 * - normalized form always ends with `/`
 */
object ConnectorUrlValidator {

    sealed class Result {
        data class Valid(val normalized: String) : Result()
        data class Invalid(val reason: Reason) : Result()
    }

    enum class Reason {
        BLANK,
        INVALID_FORMAT,
        UNSUPPORTED_SCHEME,
        MISSING_HOST,
        PATH_NOT_ALLOWED,
    }

    fun validate(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Invalid(Reason.BLANK)

        val candidate = if (SCHEME_PREFIX_REGEX.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val schemeMatch = SCHEME_REGEX.find(candidate)
            ?: return Result.Invalid(Reason.INVALID_FORMAT)
        val scheme = schemeMatch.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.Invalid(Reason.UNSUPPORTED_SCHEME)
        }

        val match = ABSOLUTE_URL_REGEX.matchEntire(candidate)
            ?: return Result.Invalid(Reason.INVALID_FORMAT)

        val host = match.groupValues[2]
        if (host.isBlank()) return Result.Invalid(Reason.MISSING_HOST)

        val port = match.groupValues[3].takeIf { it.isNotBlank() }
        if (port != null) {
            val portNumber = port.toIntOrNull()
                ?: return Result.Invalid(Reason.INVALID_FORMAT)
            if (portNumber !in 1..65535) {
                return Result.Invalid(Reason.INVALID_FORMAT)
            }
        }

        val path = match.groupValues[4]
        if (path.isNotEmpty() && path != "/") {
            return Result.Invalid(Reason.PATH_NOT_ALLOWED)
        }

        val queryOrFragment = match.groupValues[5]
        if (queryOrFragment.isNotEmpty()) {
            return Result.Invalid(Reason.INVALID_FORMAT)
        }

        val normalized = buildString {
            append(scheme)
            append("://")
            append(host)
            if (port != null) {
                append(':')
                append(port)
            }
            append('/')
        }
        return Result.Valid(normalized)
    }

    fun normalizeOrNull(raw: String): String? = when (val result = validate(raw)) {
        is Result.Valid -> result.normalized
        is Result.Invalid -> null
    }

    private val SCHEME_PREFIX_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
    private val SCHEME_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):""")
    private val ABSOLUTE_URL_REGEX =
        Regex("""^(https?):\/\/([^\/:?#]+)(?::(\d{1,5}))?(\/[^?#]*)?([?#].*)?$""", RegexOption.IGNORE_CASE)
}

/** User-facing validation copy for [ConnectorUrlValidator.Reason]. */
fun ConnectorUrlValidator.Reason.toUserMessage(): String = when (this) {
    ConnectorUrlValidator.Reason.BLANK -> "URL is required."
    ConnectorUrlValidator.Reason.INVALID_FORMAT -> "Enter a valid absolute URL."
    ConnectorUrlValidator.Reason.UNSUPPORTED_SCHEME -> "Only http and https URLs are supported."
    ConnectorUrlValidator.Reason.MISSING_HOST -> "URL must include a host."
    ConnectorUrlValidator.Reason.PATH_NOT_ALLOWED ->
        "URL must be an origin only (no path). Example: http://192.168.1.10:8080/"
}
