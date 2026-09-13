package com.jajusri.venture.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.jajusri.venture.feature.transaction.domain.port.DeviceKeySecurityLevel
import com.jajusri.venture.feature.transaction.domain.port.DeviceKeyLifecycleStatus
import com.jajusri.venture.feature.transaction.domain.port.DeviceSigningIdentity
import com.jajusri.venture.feature.transaction.domain.port.DeviceSigningResult
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVartalapDeviceKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : VartalapDeviceKeyStore {
    private val preferences = context.getSharedPreferences("vartalap_device_signing_identity", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val mutex = Mutex()

    override suspend fun getCurrentIdentity(): DeviceSigningIdentity? = mutex.withLock {
        val deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: return@withLock null
        val version = preferences.getInt(KEY_VERSION, 0).takeIf { it > 0 } ?: return@withLock null
        readIdentity(deviceId, version)
    }

    override suspend fun getOrCreateIdentity(deviceId: String): DeviceSigningIdentity = mutex.withLock {
        require(deviceId.isNotBlank())
        val storedDeviceId = preferences.getString(KEY_DEVICE_ID, null)
        check(storedDeviceId == null || storedDeviceId == deviceId) {
            "A Vartalap identity already exists for this installation"
        }
        val currentVersion = preferences.getInt(KEY_VERSION, 0).takeIf { it > 0 }
        if (currentVersion != null) {
            readIdentity(deviceId, currentVersion)?.let { return@withLock it }
            error("Active Vartalap signing key is unavailable")
        }
        createIdentity(deviceId, 1)
    }

    override suspend fun rotate(deviceId: String): DeviceSigningIdentity = mutex.withLock {
        require(deviceId.isNotBlank())
        check(preferences.getString(KEY_DEVICE_ID, null) == deviceId) {
            "Cannot rotate a different logical device identity"
        }
        val current = preferences.getInt(KEY_VERSION, 0)
        check(current > 0 && readIdentity(deviceId, current) != null) {
            "Active Vartalap signing key is unavailable"
        }
        createIdentity(deviceId, current + 1)
    }

    override suspend fun inspect(deviceId: String, keyVersion: Int): DeviceSigningIdentity? = mutex.withLock {
        if (deviceId.isBlank() || keyVersion <= 0) return@withLock null
        readIdentity(deviceId, keyVersion)
    }

    override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray): DeviceSigningResult = mutex.withLock {
        if (boundedBytes.isEmpty()) return@withLock DeviceSigningResult.InvalidInput
        val storedIdentity = readIdentity(identity.deviceId, identity.keyVersion)
            ?: return@withLock DeviceSigningResult.KeyUnavailable
        if (storedIdentity.keyId != identity.keyId ||
            !storedIdentity.publicKey.contentEquals(identity.publicKey) ||
            storedIdentity.publicKeyFingerprint != identity.publicKeyFingerprint
        ) return@withLock DeviceSigningResult.InvalidInput
        val privateKey = readPrivateKey(identity.deviceId, identity.keyVersion)
            ?: return@withLock DeviceSigningResult.KeyUnavailable
        runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initSign(privateKey)
                update(boundedBytes)
            }.sign()
        }.fold(DeviceSigningResult::Success) { DeviceSigningResult.KeyUnavailable }
    }

    override suspend fun remove(deviceId: String, keyVersion: Int): Boolean = mutex.withLock {
        val keyAlias = alias(deviceId, keyVersion)
        if (!keyStore.containsAlias(keyAlias)) return@withLock false
        keyStore.deleteEntry(keyAlias)
        if (preferences.getString(KEY_DEVICE_ID, null) == deviceId && preferences.getInt(KEY_VERSION, 0) == keyVersion) {
            preferences.edit { remove(KEY_DEVICE_ID); remove(KEY_VERSION); remove(KEY_CREATED_AT) }
        }
        true
    }

    private fun createIdentity(deviceId: String, version: Int): DeviceSigningIdentity {
        val keyAlias = alias(deviceId, version)
        if (!keyStore.containsAlias(keyAlias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
                generateKeyPair()
            }
        }
        val createdAt = System.currentTimeMillis()
        preferences.edit {
            putString(KEY_DEVICE_ID, deviceId)
            putInt(KEY_VERSION, version)
            putLong(KEY_CREATED_AT, createdAt)
            putLong("created_at.$deviceId.$version", createdAt)
        }
        return readIdentity(deviceId, version) ?: error("Vartalap signing key could not be read")
    }

    private fun readIdentity(deviceId: String, version: Int): DeviceSigningIdentity? {
        val entry = keyStore.getEntry(alias(deviceId, version), null) as? KeyStore.PrivateKeyEntry ?: return null
        val publicKey = entry.certificate.publicKey.encoded
        val keyInfo = runCatching {
            KeyFactory.getInstance(entry.privateKey.algorithm, ANDROID_KEYSTORE)
                .getKeySpec(entry.privateKey, android.security.keystore.KeyInfo::class.java)
        }.getOrNull()
        val security = when {
            keyInfo == null || !keyInfo.isInsideSecureHardware -> DeviceKeySecurityLevel.SecureKeystore
            else -> DeviceKeySecurityLevel.HardwareBacked
        }
        val currentVersion = preferences.getInt(KEY_VERSION, 0)
        val status = if (version == currentVersion) DeviceKeyLifecycleStatus.Active else DeviceKeyLifecycleStatus.Superseded
        return DeviceSigningIdentity(deviceId, alias(deviceId, version), version, publicKey, fingerprint(publicKey), createdAt(deviceId, version), security, status)
    }

    private fun createdAt(deviceId: String, version: Int): Long =
        preferences.getLong("created_at.$deviceId.$version", preferences.getLong(KEY_CREATED_AT, 0L))

    private fun readPrivateKey(deviceId: String, version: Int): PrivateKey? =
        (keyStore.getEntry(alias(deviceId, version), null) as? KeyStore.PrivateKeyEntry)?.privateKey

    private fun alias(deviceId: String, version: Int) = "venture.vartalap.device.$deviceId.v$version"
    private fun fingerprint(bytes: ByteArray) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_VERSION = "key_version"
        const val KEY_CREATED_AT = "created_at"
    }
}
