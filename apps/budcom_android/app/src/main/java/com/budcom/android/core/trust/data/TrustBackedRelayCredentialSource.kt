package com.budcom.android.core.trust.data

import com.budcom.android.core.trust.domain.TrustCredentialStore
import com.budcom.android.feature.transaction.domain.port.BusinessDeviceCredential
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [RelayCredentialSource] this app should use once wired in -- see the final report's
 * Gate 5 finding: `feature/transaction/data/di/TransactionModule.kt` currently binds
 * `RelayCredentialSource { _, _ -> null }` (an explicit stub), and swapping that one line for this
 * class is a change to a file inside this round's STRICT DO-NOT-TOUCH `feature/transaction` area,
 * so it has NOT been made here pending explicit sign-off. `KeystoreRelayEnvelopeAuthenticator`
 * (the only consumer of this port) already handles the current stub's `null` correctly -- fail
 * closed, no envelope is ever authenticated -- so leaving the stub in place is safe, just inert.
 */
@Singleton
class TrustBackedRelayCredentialSource @Inject constructor(
    private val credentialStore: TrustCredentialStore,
) : RelayCredentialSource {
    override suspend fun credentialFor(businessId: String, deviceId: String): BusinessDeviceCredential? {
        val stored = credentialStore.current() ?: return null
        // Business binding (Gate 3D): the comparison against the REQUESTED businessId/deviceId is
        // always explicit here, never inferred from Tally company name, Party name, or assumed
        // because only one credential happens to be on file.
        if (stored.businessId != businessId || stored.deviceId != deviceId) return null
        if (System.currentTimeMillis() >= stored.expiresAtEpochMillis) return null
        return BusinessDeviceCredential(
            credentialVersion = stored.credentialVersion,
            businessId = stored.businessId,
            actorId = stored.actorId,
            deviceId = stored.deviceId,
            authority = stored.signatureBase64,
            issuedAtEpochMillis = stored.issuedAtEpochMillis,
            expiresAtEpochMillis = stored.expiresAtEpochMillis,
            credentialEpoch = stored.authorityEpoch,
            issuerReference = "${stored.issuerId}:${stored.issuerKeyId}",
            verificationReference = stored.credentialId,
        )
    }
}
