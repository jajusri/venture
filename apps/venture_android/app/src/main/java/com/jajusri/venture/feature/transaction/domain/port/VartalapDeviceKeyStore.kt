package com.jajusri.venture.feature.transaction.domain.port

data class DeviceSigningIdentity(
    val deviceId: String,
    val keyId: String,
    val keyVersion: Int,
    val publicKey: ByteArray,
    val publicKeyFingerprint: String,
    val createdAtEpochMillis: Long,
    val securityLevel: DeviceKeySecurityLevel,
    val lifecycleStatus: DeviceKeyLifecycleStatus = DeviceKeyLifecycleStatus.Active,
) {
    init {
        require(deviceId.isNotBlank())
        require(keyId.isNotBlank())
        require(keyVersion > 0)
        require(publicKey.isNotEmpty())
        require(publicKeyFingerprint.isNotBlank())
    }

    override fun equals(other: Any?): Boolean = other is DeviceSigningIdentity &&
        deviceId == other.deviceId && keyId == other.keyId && keyVersion == other.keyVersion &&
        publicKey.contentEquals(other.publicKey) && publicKeyFingerprint == other.publicKeyFingerprint &&
        createdAtEpochMillis == other.createdAtEpochMillis && securityLevel == other.securityLevel

    override fun hashCode(): Int = listOf(deviceId, keyId, keyVersion, publicKeyFingerprint, createdAtEpochMillis, securityLevel).hashCode() * 31 + publicKey.contentHashCode()
}

enum class DeviceKeySecurityLevel { SecureKeystore, HardwareBacked, StrongBoxBacked, Unavailable }
enum class DeviceKeyLifecycleStatus { Active, Superseded, Revoked, LostOrUnavailable }

sealed interface DeviceSigningResult {
    data class Success(val signature: ByteArray) : DeviceSigningResult
    data object IdentityUnavailable : DeviceSigningResult
    data object KeyUnavailable : DeviceSigningResult
    data object InvalidInput : DeviceSigningResult
}

interface VartalapDeviceKeyStore {
    suspend fun getCurrentIdentity(): DeviceSigningIdentity?
    suspend fun getOrCreateIdentity(deviceId: String): DeviceSigningIdentity
    suspend fun rotate(deviceId: String): DeviceSigningIdentity
    suspend fun inspect(deviceId: String, keyVersion: Int): DeviceSigningIdentity?
    suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray): DeviceSigningResult
    suspend fun remove(deviceId: String, keyVersion: Int): Boolean
}
