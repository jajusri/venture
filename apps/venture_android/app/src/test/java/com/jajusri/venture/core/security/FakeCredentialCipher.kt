package com.jajusri.venture.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A real AES-256-GCM [CredentialCipher] backed by an in-memory (non-`AndroidKeyStore`) JCE key —
 * not a trivial no-op double. This lets JVM tests genuinely exercise IV-uniqueness,
 * tamper-detection, and missing/wrong-key rejection using only plain-JVM crypto, matching this
 * phase's "no JVM tests depend on a real AndroidKeyStore provider" constraint while still proving
 * the same AES/GCM contract [AndroidKeystoreCredentialCipher] provides in production.
 */
class FakeCredentialCipher : CredentialCipher {
    private var key: SecretKey? = generateKey()

    /** Simulates the key having been lost (Keystore cleared/reset). */
    fun dropKey() {
        key = null
    }

    override fun hasKey(): Boolean = key != null

    override fun encrypt(plaintext: ByteArray): CredentialEncryptionResult {
        // Mirrors AndroidKeystoreCredentialCipher: encryption regenerates the key if needed
        // rather than failing — "missing key" is a decrypt-time failure (see decrypt below),
        // not an encrypt-time one.
        val currentKey = key ?: generateKey().also { key = it }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, currentKey)
        val ciphertext = cipher.doFinal(plaintext)
        return CredentialEncryptionResult.Success(
            EncryptedPayload(ciphertext = ciphertext, iv = cipher.iv, formatVersion = CREDENTIAL_CIPHER_FORMAT_VERSION),
        )
    }

    override fun decrypt(payload: EncryptedPayload): CredentialDecryptionResult {
        if (payload.formatVersion != CREDENTIAL_CIPHER_FORMAT_VERSION) return CredentialDecryptionResult.InvalidCiphertext
        val currentKey = key ?: return CredentialDecryptionResult.KeyMissing
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, currentKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv))
            CredentialDecryptionResult.Success(cipher.doFinal(payload.ciphertext))
        } catch (e: Exception) {
            CredentialDecryptionResult.InvalidCiphertext
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
