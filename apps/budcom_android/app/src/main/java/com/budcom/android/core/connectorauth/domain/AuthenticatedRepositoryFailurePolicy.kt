package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.common.AppError
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Well-known [AppError.Remote.code] values a caller can pattern-match on for the two
 * authentication-rejection outcomes. Deliberately reuses the existing [AppError.Remote] variant
 * rather than widening the sealed [AppError] hierarchy — several exhaustive `when` blocks over
 * [AppError] exist in ViewModels and UI-state mappers outside this phase's approved scope, and
 * adding a new subclass would force edits there.
 */
const val AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE = "SECURE_PAIRING_REQUIRED"
const val AUTHENTICATED_ACCESS_DENIED_CODE = "AUTHENTICATED_ACCESS_DENIED"

/**
 * Well-known [AppError.Remote.code] a caller can pattern-match on to recognize the Connector's
 * HTTP 400 `NO_COMPANY_SELECTED` session-validation outcome (TD-013) — the one 400 sub-case that
 * is auto-recoverable via company reselection. See
 * [com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult.ValidationFailure.isNoCompanySelected].
 */
const val AUTHENTICATED_NO_COMPANY_SELECTED_CODE = "NO_COMPANY_SELECTED"

/**
 * Central mapping from a non-Success [AuthenticatedConnectorResult] to an [AppError], shared by
 * every authenticated repository adapter. Never mutates Room. [AuthenticatedConnectorResult.Unauthorized]
 * is the only outcome that mutates the vault: it marks the credential identified by
 * [AuthenticatedConnectorResult.Unauthorized.credentialId] — the credential actually used by the
 * rejected request, carried through from [com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorContext.credentialId]
 * — RE_PAIR_REQUIRED. This never reads "whichever credential is current" first: it calls
 * [SecureCredentialVault.markRePairRequired] directly with that request's own credential ID, so
 * the vault's own atomic credential-ID match check is what decides whether the mutation applies.
 * A credential replaced by a newer re-pair between the failing request being sent and this
 * handler running has a different ID, the match fails, and the newer credential is left untouched.
 */
interface AuthenticatedRepositoryFailurePolicy {
    suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError
}

@Singleton
class DefaultAuthenticatedRepositoryFailurePolicy @Inject constructor(
    private val vault: SecureCredentialVault,
) : AuthenticatedRepositoryFailurePolicy {

    override suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError = when (result) {
        is AuthenticatedConnectorResult.Unauthorized -> {
            vault.markRePairRequired(result.credentialId)
            AppError.Remote(
                httpStatus = 401,
                code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE,
                message = "This device needs to re-pair with the Connector.",
            )
        }

        AuthenticatedConnectorResult.Forbidden -> AppError.Remote(
            httpStatus = 403,
            code = AUTHENTICATED_ACCESS_DENIED_CODE,
            message = "This device is not authorized to perform this action.",
        )

        // Local vault states the port independently discovered — never a server rejection, so
        // the vault is never mutated here (it already reflects one of these states).
        AuthenticatedConnectorResult.Unpaired,
        AuthenticatedConnectorResult.PendingVerification,
        AuthenticatedConnectorResult.RePairRequired,
        AuthenticatedConnectorResult.CredentialUnavailable,
        -> AppError.Remote(
            httpStatus = null,
            code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE,
            message = "This device needs to re-pair with the Connector.",
        )

        is AuthenticatedConnectorResult.ValidationFailure -> AppError.Remote(
            httpStatus = 400,
            code = if (result.isNoCompanySelected) AUTHENTICATED_NO_COMPANY_SELECTED_CODE else result.sanitizedCode,
            message = "The Connector rejected the request.",
        )

        AuthenticatedConnectorResult.NotFound -> AppError.Remote(404, null, "The requested resource was not found.")
        AuthenticatedConnectorResult.Conflict -> AppError.Remote(409, null, "The request conflicted with the current Connector state.")
        AuthenticatedConnectorResult.RateLimited -> AppError.Remote(429, null, "Too many requests. Try again shortly.")
        is AuthenticatedConnectorResult.ServerFailure -> AppError.Remote(result.httpStatus, null, "The Connector reported a server error.")
        AuthenticatedConnectorResult.TransportFailure -> AppError.Offline()
        AuthenticatedConnectorResult.MalformedResponse -> AppError.Serialization("The Connector response could not be parsed.")
        AuthenticatedConnectorResult.Cancelled -> AppError.Message("The request was cancelled.")
        is AuthenticatedConnectorResult.Success -> error("mapFailure must not be called with a Success result")
    }
}
