package com.budcom.android.core.trust.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budcom.android.core.security.CredentialCipher
import com.budcom.android.core.security.CredentialDecryptionResult
import com.budcom.android.core.security.CredentialEncryptionResult
import com.budcom.android.core.security.EncryptedPayload
import com.budcom.android.core.trust.domain.StoredTrustCredential
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** JSON-serializable mirror of [StoredTrustCredential] for the encrypted blob -- kept separate so
 * the domain model itself carries no serialization annotations. */
@Serializable
private data class PersistedTrustCredentialJson(
    val credentialVersion: Int, val credentialId: String, val businessId: String, val actorId: String, val membershipId: String,
    val deviceId: String, val deviceKeyId: String, val deviceKeyVersion: Int, val devicePublicKeyFingerprint: String,
    val authorityScope: List<String>, val authorityEpoch: Long, val issuedAtEpochMillis: Long, val notBeforeEpochMillis: Long,
    val expiresAtEpochMillis: Long, val issuerId: String, val issuerKeyId: String, val signatureBase64: String,
)

private fun StoredTrustCredential.toJson() = PersistedTrustCredentialJson(
    credentialVersion, credentialId, businessId, actorId, membershipId, deviceId, deviceKeyId, deviceKeyVersion,
    devicePublicKeyFingerprint, authorityScope, authorityEpoch, issuedAtEpochMillis, notBeforeEpochMillis,
    expiresAtEpochMillis, issuerId, issuerKeyId, signatureBase64,
)
private fun PersistedTrustCredentialJson.toDomain() = StoredTrustCredential(
    credentialVersion, credentialId, businessId, actorId, membershipId, deviceId, deviceKeyId, deviceKeyVersion,
    devicePublicKeyFingerprint, authorityScope, authorityEpoch, issuedAtEpochMillis, notBeforeEpochMillis,
    expiresAtEpochMillis, issuerId, issuerKeyId, signatureBase64,
)

private val Context.trustCredentialDataStore: DataStore<Preferences> by preferencesDataStore(name = "trust_credential_vault")

/** Exact shape of `core/pairing/data/local/SecureCredentialVault.kt`'s `DataStoreSecureCredentialVault`:
 * the credential is serialized to JSON, then encrypted as a whole via [CredentialCipher] before it
 * ever reaches disk -- one opaque ciphertext blob rather than per-field plaintext preferences. */
@Singleton
class SecureTrustCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val credentialCipher: CredentialCipher,
) : TrustCredentialStore {
    private val dataStore = context.trustCredentialDataStore
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun current(): StoredTrustCredential? = (readOutcome() as? TrustCredentialReadOutcome.Present)?.credential

    override suspend fun readOutcome(): TrustCredentialReadOutcome = try { readOutcomeOrThrow() } catch (e: IOException) {
        TrustCredentialReadOutcome.Unreadable
    } catch (e: IllegalArgumentException) {
        // Malformed base64 or JSON in a stored field.
        TrustCredentialReadOutcome.Unreadable
    }

    private suspend fun readOutcomeOrThrow(): TrustCredentialReadOutcome {
        val prefs = dataStore.data.first()
        val ciphertextB64 = prefs[KEY_CIPHERTEXT]
        val ivB64 = prefs[KEY_IV]
        val formatVersion = prefs[KEY_FORMAT_VERSION]
        if (ciphertextB64 == null || ivB64 == null || formatVersion == null) return TrustCredentialReadOutcome.NoRecord
        val payload = EncryptedPayload(
            ciphertext = Base64.getDecoder().decode(ciphertextB64),
            iv = Base64.getDecoder().decode(ivB64),
            formatVersion = formatVersion,
        )
        return when (val result = credentialCipher.decrypt(payload)) {
            is CredentialDecryptionResult.Success -> {
                val decoded = json.decodeFromString(PersistedTrustCredentialJson.serializer(), result.plaintext.toString(Charsets.UTF_8))
                TrustCredentialReadOutcome.Present(decoded.toDomain())
            }
            CredentialDecryptionResult.KeyMissing, CredentialDecryptionResult.InvalidCiphertext -> TrustCredentialReadOutcome.Unreadable
        }
    }

    override suspend fun store(credential: StoredTrustCredential): Unit = mutex.withLock {
        val plaintext = json.encodeToString(PersistedTrustCredentialJson.serializer(), credential.toJson()).toByteArray(Charsets.UTF_8)
        val encrypted = when (val result = credentialCipher.encrypt(plaintext)) {
            is CredentialEncryptionResult.Success -> result.payload
            CredentialEncryptionResult.KeystoreUnavailable -> throw IOException("Trust credential encryption failed: platform Keystore unavailable")
        }
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_CIPHERTEXT] = Base64.getEncoder().encodeToString(encrypted.ciphertext)
            prefs[KEY_IV] = Base64.getEncoder().encodeToString(encrypted.iv)
            prefs[KEY_FORMAT_VERSION] = encrypted.formatVersion
        }
    }

    override suspend fun clear() {
        mutex.withLock { dataStore.edit { it.clear() } }
    }

    private companion object {
        val KEY_CIPHERTEXT = stringPreferencesKey("ciphertext_b64")
        val KEY_IV = stringPreferencesKey("iv_b64")
        val KEY_FORMAT_VERSION = intPreferencesKey("format_version")
    }
}
