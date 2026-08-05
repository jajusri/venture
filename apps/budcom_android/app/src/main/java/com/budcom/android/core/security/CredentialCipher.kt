package com.budcom.android.core.security

/** Ciphertext plus everything (beyond the caller's own key) needed to later decrypt it. */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val formatVersion: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return formatVersion == other.formatVersion && ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + formatVersion
        return result
    }

    override fun toString(): String = "EncryptedPayload(formatVersion=$formatVersion, ciphertext=<redacted>, iv=<redacted>)"
}

sealed class CredentialEncryptionResult {
    data class Success(val payload: EncryptedPayload) : CredentialEncryptionResult()

    /** Key generation or encryption itself failed at the platform/provider level. */
    data object KeystoreUnavailable : CredentialEncryptionResult()
}

sealed class CredentialDecryptionResult {
    data class Success(val plaintext: ByteArray) : CredentialDecryptionResult()

    /** No key exists under the expected alias — e.g. Keystore was cleared/reset. */
    data object KeyMissing : CredentialDecryptionResult()

    /** Key exists but the ciphertext/IV/tag don't validate against it (corrupt or tampered). */
    data object InvalidCiphertext : CredentialDecryptionResult()
}

/**
 * Narrow encrypt/decrypt seam over the platform Keystore, so pairing persistence code
 * ([com.budcom.android.core.pairing.data.local.SecureCredentialVault]) never touches
 * `AndroidKeyStore`/`Cipher` directly. Every failure mode is a distinct, explicit result value —
 * never a silently-generated replacement key masquerading as the original credential, and never
 * a plaintext fallback.
 */
interface CredentialCipher {
    /** Encrypts [plaintext]. Generates the underlying key on first use if one doesn't exist yet. */
    fun encrypt(plaintext: ByteArray): CredentialEncryptionResult

    fun decrypt(payload: EncryptedPayload): CredentialDecryptionResult

    /** True if the underlying key currently exists (has been generated at least once). */
    fun hasKey(): Boolean
}
