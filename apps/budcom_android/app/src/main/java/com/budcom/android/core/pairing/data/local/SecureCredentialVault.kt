package com.budcom.android.core.pairing.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.CredentialCipher
import com.budcom.android.core.security.CredentialDecryptionResult
import com.budcom.android.core.security.CredentialEncryptionResult
import com.budcom.android.core.security.EncryptedPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed class SecureCredentialVaultWriteResult {
    data object Stored : SecureCredentialVaultWriteResult()

    /** Encryption failed at the platform Keystore level — nothing was persisted or replaced. */
    data object KeystoreUnavailable : SecureCredentialVaultWriteResult()
}

/** Outcome of [SecureCredentialVault.updateVerifiedEndpoint]. See that method's doc comment. */
sealed class SecureCredentialVaultEndpointUpdateResult {
    data object Updated : SecureCredentialVaultEndpointUpdateResult()

    /** No record exists to update. */
    data object NoRecord : SecureCredentialVaultEndpointUpdateResult()

    /** The current record's connectorId does not match — the caller verified a different Connector. */
    data object ConnectorIdMismatch : SecureCredentialVaultEndpointUpdateResult()
}

/**
 * Outcome of a safe, non-throwing enrollment-history read. Unlike [SecureCredentialVault.read],
 * which collapses "never enrolled" and "unreadable" into one `null`, this distinguishes them —
 * required so a corrupted/unreadable record is never treated as an empty, never-enrolled vault
 * (which would wrongly make a previously-secured installation eligible for legacy transport again).
 */
sealed class SecureCredentialVaultReadOutcome {
    /** No pairing record has ever been written on this device. */
    data object NoRecord : SecureCredentialVaultReadOutcome()

    /** A fully-parsed, valid record — see [record] for its current [SecurePairingCredentialState]. */
    data class Present(val record: SecurePairingCredentialRecord) : SecureCredentialVaultReadOutcome()

    /**
     * A record was (or may have been) written, but it could not be read back intact — underlying
     * storage corruption, or a persisted field that no longer parses (e.g. an unrecognized state
     * value). Never treated as [NoRecord]; the caller must not fall back to legacy transport.
     */
    data object Unreadable : SecureCredentialVaultReadOutcome()
}

/**
 * Strictly one-record-per-Connector/device trust relationship, persisted with the bearer
 * credential always encrypted (via [CredentialCipher]) before it ever reaches disk. Never stores
 * or exposes the plaintext credential except transiently through [readDecryptedCredential] for
 * the caller's immediate use. Never touches Room — a Keystore/vault failure changes only this
 * store's own state, cached accounting data is a completely separate repository this class has
 * no dependency on and therefore cannot affect.
 */
interface SecureCredentialVault {
    /** Metadata for the current trust record, if any. Never includes the decrypted credential. */
    suspend fun read(): SecurePairingCredentialRecord?

    /**
     * Safe, non-throwing variant of [read] that distinguishes a genuinely empty vault from an
     * unreadable/corrupted one — see [SecureCredentialVaultReadOutcome]. Callers making a
     * legacy-transport-eligibility or startup-routing decision must use this, never [read] alone.
     */
    suspend fun readOutcome(): SecureCredentialVaultReadOutcome

    /**
     * Encrypts [rawCredential] and atomically replaces any existing record with a new one in
     * [SecurePairingCredentialState.PENDING_VERIFICATION]. The plaintext itself is never
     * returned again — see [readDecryptedCredential].
     */
    suspend fun storePendingVerification(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult

    /**
     * Encrypts and atomically publishes an already-verified replacement as ACTIVE. Used only for
     * re-pairing over an existing ACTIVE record: the old record remains untouched until the new
     * credential has completed its pinned self-status proof.
     */
    suspend fun storeVerifiedActive(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
        verifiedAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult

    /** Promotes the current record to ACTIVE, only if its credentialId matches [credentialId]. */
    suspend fun markActive(credentialId: String, verifiedAtEpochMillis: Long): Boolean

    /** Marks the current record RE_PAIR_REQUIRED, only if its credentialId matches [credentialId]. */
    suspend fun markRePairRequired(credentialId: String): Boolean

    /**
     * TD-017: atomically replaces the current record's `host`/`securePort` with [host]/[securePort]
     * — every other field (connectorId, transportFingerprint, fingerprintAlgorithm,
     * transportIdentityVersion, credential, state, timestamps) is left exactly as it was. This
     * method performs no verification of its own and trusts its caller completely: it must only
     * ever be called with a [host]/[securePort] the caller has already cryptographically verified
     * belongs to [connectorId] (see `AuthenticatedConnectorEndpointResolver`) — passing an
     * unverified, mDNS-supplied endpoint here would defeat the entire point of pinning. Rejects
     * with [SecureCredentialVaultEndpointUpdateResult.ConnectorIdMismatch] if the current record's
     * connectorId does not equal [connectorId] — a defense-in-depth guard in case the record was
     * replaced (e.g. by a concurrent re-pair) between verification and this call; the mismatched
     * record is left completely untouched in that case. Never resets state, never touches the
     * credential, never marks RE_PAIR_REQUIRED.
     */
    suspend fun updateVerifiedEndpoint(connectorId: String, host: String, securePort: Int): SecureCredentialVaultEndpointUpdateResult

    /** Removes the trust record entirely. */
    suspend fun clear()

    /** Decrypts the current record's bearer credential for one-shot use, or null if no record exists. */
    suspend fun readDecryptedCredential(): CredentialDecryptionResult?
}

private val Context.securePairingVaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_pairing_credential_vault")

@Singleton
class DataStoreSecureCredentialVault @Inject constructor(
    @ApplicationContext context: Context,
    private val credentialCipher: CredentialCipher,
) : SecureCredentialVault {

    private val dataStore = context.securePairingVaultDataStore
    private val mutex = Mutex()

    override suspend fun read(): SecurePairingCredentialRecord? = dataStore.data.map { it.toRecord() }.first()

    override suspend fun readOutcome(): SecureCredentialVaultReadOutcome = try {
        dataStore.data.first().toReadOutcome()
    } catch (e: IOException) {
        // Includes androidx.datastore.core.CorruptionException, which extends IOException — a
        // corrupted store must never be reported as an empty, never-enrolled vault.
        SecureCredentialVaultReadOutcome.Unreadable
    }

    override suspend fun storePendingVerification(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult = mutex.withLock {
        val encrypted = when (val result = credentialCipher.encrypt(rawCredential.toByteArray(Charsets.UTF_8))) {
            is CredentialEncryptionResult.Success -> result.payload
            is CredentialEncryptionResult.KeystoreUnavailable -> return SecureCredentialVaultWriteResult.KeystoreUnavailable
        }

        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_CREDENTIAL_ID] = credentialId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_CIPHERTEXT] = Base64.getEncoder().encodeToString(encrypted.ciphertext)
            prefs[KEY_IV] = Base64.getEncoder().encodeToString(encrypted.iv)
            prefs[KEY_FORMAT_VERSION] = encrypted.formatVersion
            prefs[KEY_CREATED_AT] = createdAtEpochMillis
            prefs[KEY_STATE] = SecurePairingCredentialState.PENDING_VERIFICATION.name
            prefs[KEY_CONNECTOR_ID] = endpoint.connectorId
            prefs[KEY_CONNECTOR_NAME] = endpoint.connectorName
            prefs[KEY_HOST] = endpoint.host
            prefs[KEY_SECURE_PORT] = endpoint.securePort
            prefs[KEY_TRANSPORT_FINGERPRINT] = endpoint.transportFingerprint
            prefs[KEY_FINGERPRINT_ALGORITHM] = endpoint.fingerprintAlgorithm
            prefs[KEY_TRANSPORT_IDENTITY_VERSION] = endpoint.transportIdentityVersion
        }
        SecureCredentialVaultWriteResult.Stored
    }

    override suspend fun storeVerifiedActive(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
        verifiedAtEpochMillis: Long,
    ): SecureCredentialVaultWriteResult = mutex.withLock {
        val encrypted = when (val result = credentialCipher.encrypt(rawCredential.toByteArray(Charsets.UTF_8))) {
            is CredentialEncryptionResult.Success -> result.payload
            is CredentialEncryptionResult.KeystoreUnavailable -> return SecureCredentialVaultWriteResult.KeystoreUnavailable
        }

        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_CREDENTIAL_ID] = credentialId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_CIPHERTEXT] = Base64.getEncoder().encodeToString(encrypted.ciphertext)
            prefs[KEY_IV] = Base64.getEncoder().encodeToString(encrypted.iv)
            prefs[KEY_FORMAT_VERSION] = encrypted.formatVersion
            prefs[KEY_CREATED_AT] = createdAtEpochMillis
            prefs[KEY_LAST_VERIFIED_AT] = verifiedAtEpochMillis
            prefs[KEY_STATE] = SecurePairingCredentialState.ACTIVE.name
            prefs[KEY_CONNECTOR_ID] = endpoint.connectorId
            prefs[KEY_CONNECTOR_NAME] = endpoint.connectorName
            prefs[KEY_HOST] = endpoint.host
            prefs[KEY_SECURE_PORT] = endpoint.securePort
            prefs[KEY_TRANSPORT_FINGERPRINT] = endpoint.transportFingerprint
            prefs[KEY_FINGERPRINT_ALGORITHM] = endpoint.fingerprintAlgorithm
            prefs[KEY_TRANSPORT_IDENTITY_VERSION] = endpoint.transportIdentityVersion
        }
        SecureCredentialVaultWriteResult.Stored
    }

    override suspend fun markActive(credentialId: String, verifiedAtEpochMillis: Long): Boolean = mutex.withLock {
        val current = dataStore.data.map { it.toRecord() }.first() ?: return@withLock false
        if (current.credentialId != credentialId) return@withLock false
        dataStore.edit { prefs ->
            prefs[KEY_STATE] = SecurePairingCredentialState.ACTIVE.name
            prefs[KEY_LAST_VERIFIED_AT] = verifiedAtEpochMillis
        }
        true
    }

    override suspend fun markRePairRequired(credentialId: String): Boolean = mutex.withLock {
        val current = dataStore.data.map { it.toRecord() }.first() ?: return@withLock false
        if (current.credentialId != credentialId) return@withLock false
        dataStore.edit { prefs -> prefs[KEY_STATE] = SecurePairingCredentialState.RE_PAIR_REQUIRED.name }
        true
    }

    override suspend fun updateVerifiedEndpoint(
        connectorId: String,
        host: String,
        securePort: Int,
    ): SecureCredentialVaultEndpointUpdateResult = mutex.withLock {
        val current = dataStore.data.map { it.toRecord() }.first()
            ?: return@withLock SecureCredentialVaultEndpointUpdateResult.NoRecord
        if (current.endpoint.connectorId != connectorId) {
            return@withLock SecureCredentialVaultEndpointUpdateResult.ConnectorIdMismatch
        }
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_SECURE_PORT] = securePort
        }
        SecureCredentialVaultEndpointUpdateResult.Updated
    }

    override suspend fun clear() {
        mutex.withLock { dataStore.edit { it.clear() } }
    }

    override suspend fun readDecryptedCredential(): CredentialDecryptionResult? {
        val prefs = dataStore.data.first()
        val ciphertextB64 = prefs[KEY_CIPHERTEXT] ?: return null
        val ivB64 = prefs[KEY_IV] ?: return null
        val formatVersion = prefs[KEY_FORMAT_VERSION] ?: return null
        val payload = EncryptedPayload(
            ciphertext = Base64.getDecoder().decode(ciphertextB64),
            iv = Base64.getDecoder().decode(ivB64),
            formatVersion = formatVersion,
        )
        return credentialCipher.decrypt(payload)
    }

    private fun Preferences.toRecord(): SecurePairingCredentialRecord? {
        val credentialId = this[KEY_CREDENTIAL_ID] ?: return null
        val deviceId = this[KEY_DEVICE_ID] ?: return null
        val ciphertextB64 = this[KEY_CIPHERTEXT] ?: return null
        val ivB64 = this[KEY_IV] ?: return null
        val formatVersion = this[KEY_FORMAT_VERSION] ?: return null
        val createdAt = this[KEY_CREATED_AT] ?: return null
        val stateName = this[KEY_STATE] ?: return null
        val state = runCatching { SecurePairingCredentialState.valueOf(stateName) }.getOrNull() ?: return null
        val connectorId = this[KEY_CONNECTOR_ID] ?: return null
        val connectorName = this[KEY_CONNECTOR_NAME] ?: return null
        val host = this[KEY_HOST] ?: return null
        val securePort = this[KEY_SECURE_PORT] ?: return null
        val transportFingerprint = this[KEY_TRANSPORT_FINGERPRINT] ?: return null
        val fingerprintAlgorithm = this[KEY_FINGERPRINT_ALGORITHM] ?: return null
        val transportIdentityVersion = this[KEY_TRANSPORT_IDENTITY_VERSION] ?: return null

        return SecurePairingCredentialRecord(
            credentialId = credentialId,
            deviceId = deviceId,
            encryptedCredential = EncryptedPayload(
                ciphertext = Base64.getDecoder().decode(ciphertextB64),
                iv = Base64.getDecoder().decode(ivB64),
                formatVersion = formatVersion,
            ),
            endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
                connectorId = connectorId,
                connectorName = connectorName,
                host = host,
                securePort = securePort,
                transportFingerprint = transportFingerprint,
                fingerprintAlgorithm = fingerprintAlgorithm,
                transportIdentityVersion = transportIdentityVersion,
            ),
            createdAtEpochMillis = createdAt,
            lastVerifiedAtEpochMillis = this[KEY_LAST_VERIFIED_AT],
            state = state,
        )
    }

    /**
     * [KEY_CREDENTIAL_ID] is the first field ever written by [storePendingVerification] and is
     * never removed independently of the rest of the record, so its presence is a reliable marker
     * that a record was written here at some point — even if [toRecord] itself fails to fully
     * parse the rest (e.g. a corrupted [KEY_STATE]), that must surface as [SecureCredentialVaultReadOutcome.Unreadable],
     * never as [SecureCredentialVaultReadOutcome.NoRecord].
     */
    private fun Preferences.toReadOutcome(): SecureCredentialVaultReadOutcome {
        val recordEverWritten = this[KEY_CREDENTIAL_ID] != null
        val record = try {
            toRecord()
        } catch (e: IllegalArgumentException) {
            // Malformed Base64 in a stored ciphertext/iv field — corrupted, not absent.
            null
        }
        return when {
            record != null -> SecureCredentialVaultReadOutcome.Present(record)
            recordEverWritten -> SecureCredentialVaultReadOutcome.Unreadable
            else -> SecureCredentialVaultReadOutcome.NoRecord
        }
    }

    private companion object {
        val KEY_CREDENTIAL_ID = stringPreferencesKey("credential_id")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_CIPHERTEXT = stringPreferencesKey("ciphertext_b64")
        val KEY_IV = stringPreferencesKey("iv_b64")
        val KEY_FORMAT_VERSION = intPreferencesKey("format_version")
        val KEY_CREATED_AT = longPreferencesKey("created_at_epoch_millis")
        val KEY_LAST_VERIFIED_AT = longPreferencesKey("last_verified_at_epoch_millis")
        val KEY_STATE = stringPreferencesKey("state")
        val KEY_CONNECTOR_ID = stringPreferencesKey("connector_id")
        val KEY_CONNECTOR_NAME = stringPreferencesKey("connector_name")
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_SECURE_PORT = intPreferencesKey("secure_port")
        val KEY_TRANSPORT_FINGERPRINT = stringPreferencesKey("transport_fingerprint")
        val KEY_FINGERPRINT_ALGORITHM = stringPreferencesKey("fingerprint_algorithm")
        val KEY_TRANSPORT_IDENTITY_VERSION = intPreferencesKey("transport_identity_version")
    }
}
