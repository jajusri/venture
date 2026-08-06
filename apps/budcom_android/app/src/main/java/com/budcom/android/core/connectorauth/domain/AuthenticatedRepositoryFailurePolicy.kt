package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.common.AppError
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
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
 * Central mapping from a non-Success [AuthenticatedConnectorResult] to an [AppError], shared by
 * every authenticated repository adapter. Never mutates Room. [AuthenticatedConnectorResult.Unauthorized]
 * is the only outcome that mutates the vault: it marks the *current* ACTIVE credential
 * RE_PAIR_REQUIRED, relying on [SecureCredentialVault.markRePairRequired]'s own credential-ID
 * match check so a credential replaced between the failing request and this handler running is
 * never invalidated by an older request's rejection.
 */
interface AuthenticatedRepositoryFailurePolicy {
    suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError
}

@Singleton
class DefaultAuthenticatedRepositoryFailurePolicy @Inject constructor(
    private val vault: SecureCredentialVault,
) : AuthenticatedRepositoryFailurePolicy {

    override suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError = when (result) {
        AuthenticatedConnectorResult.Unauthorized -> {
            val current = vault.read()
            if (current != null && current.state == SecurePairingCredentialState.ACTIVE) {
                vault.markRePairRequired(current.credentialId)
            }
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
            code = result.sanitizedCode,
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
