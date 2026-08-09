package com.budcom.android.core.pairing.data.local

import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.CredentialCipher
import com.budcom.android.core.security.CredentialDecryptionResult
import com.budcom.android.core.security.CredentialEncryptionResult
import com.budcom.android.core.security.FakeCredentialCipher

/** Shared "disk" a [FakeSecureCredentialVault] reads/writes — lets a test simulate the same
 * underlying storage surviving a new repository instance being constructed ("process restart"). */
class InMemoryVaultBackingStore {
    var record: SecurePairingCredentialRecord? = null

    /** When true, simulates a corrupted/unreadable record — see [SecureCredentialVaultReadOutcome.Unreadable]. */
    var unreadable: Boolean = false
}

/**
 * In-memory double for [SecureCredentialVault], faithful to
 * [DataStoreSecureCredentialVault]'s own encrypt-before-persist logic (it really does call
 * through [CredentialCipher], defaulting to [FakeCredentialCipher] — a real AES-GCM cipher, not a
 * no-op) rather than a shortcut that stores the plaintext directly.
 */
class FakeSecureCredentialVault(
    private val cipher: CredentialCipher = FakeCredentialCipher(),
    private val backingStore: InMemoryVaultBackingStore = InMemoryVaultBackingStore(),
) : SecureCredentialVault {

    override suspend fun read(): SecurePairingCredentialRecord? = backingStore.record

    override suspend fun readOutcome(): SecureCredentialVaultReadOutcome = when {
        backingStore.unreadable -> SecureCredentialVaultReadOutcome.Unreadable
        backingStore.record != null -> SecureCredentialVaultReadOutcome.Present(backingStore.record!!)
        else -> SecureCredentialVaultReadOutcome.NoRecord
    }

    override suspend fun storePendingVerification(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult {
        val encrypted = when (val result = cipher.encrypt(rawCredential.toByteArray(Charsets.UTF_8))) {
            is CredentialEncryptionResult.Success -> result.payload
            is CredentialEncryptionResult.KeystoreUnavailable -> return SecureCredentialVaultWriteResult.KeystoreUnavailable
        }
        backingStore.record = SecurePairingCredentialRecord(
            credentialId = credentialId,
            deviceId = deviceId,
            encryptedCredential = encrypted,
            endpoint = endpoint,
            createdAtEpochMillis = createdAtEpochMillis,
            lastVerifiedAtEpochMillis = null,
            state = SecurePairingCredentialState.PENDING_VERIFICATION,
        )
        return SecureCredentialVaultWriteResult.Stored
    }

    override suspend fun markActive(credentialId: String, verifiedAtEpochMillis: Long): Boolean {
        val current = backingStore.record ?: return false
        if (current.credentialId != credentialId) return false
        backingStore.record = current.copy(state = SecurePairingCredentialState.ACTIVE, lastVerifiedAtEpochMillis = verifiedAtEpochMillis)
        return true
    }

    override suspend fun storeVerifiedActive(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
        verifiedAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult {
        val encrypted = when (val result = cipher.encrypt(rawCredential.toByteArray(Charsets.UTF_8))) {
            is CredentialEncryptionResult.Success -> result.payload
            is CredentialEncryptionResult.KeystoreUnavailable -> return SecureCredentialVaultWriteResult.KeystoreUnavailable
        }
        backingStore.record = SecurePairingCredentialRecord(
            credentialId = credentialId,
            deviceId = deviceId,
            encryptedCredential = encrypted,
            endpoint = endpoint,
            createdAtEpochMillis = createdAtEpochMillis,
            lastVerifiedAtEpochMillis = verifiedAtEpochMillis,
            state = SecurePairingCredentialState.ACTIVE,
        )
        return SecureCredentialVaultWriteResult.Stored
    }

    override suspend fun markRePairRequired(credentialId: String): Boolean {
        val current = backingStore.record ?: return false
        if (current.credentialId != credentialId) return false
        backingStore.record = current.copy(state = SecurePairingCredentialState.RE_PAIR_REQUIRED)
        return true
    }

    override suspend fun updateVerifiedEndpoint(
        connectorId: String,
        host: String,
        securePort: Int,
    ): SecureCredentialVaultEndpointUpdateResult {
        val current = backingStore.record ?: return SecureCredentialVaultEndpointUpdateResult.NoRecord
        if (current.endpoint.connectorId != connectorId) return SecureCredentialVaultEndpointUpdateResult.ConnectorIdMismatch
        backingStore.record = current.copy(endpoint = current.endpoint.copy(host = host, securePort = securePort))
        return SecureCredentialVaultEndpointUpdateResult.Updated
    }

    override suspend fun clear() {
        backingStore.record = null
    }

    override suspend fun readDecryptedCredential(): CredentialDecryptionResult? {
        val current = backingStore.record ?: return null
        return cipher.decrypt(current.encryptedCredential)
    }
}
