package com.jajusri.venture.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialCipherTest {

    private val plaintext = "device-bootstrap-bearer-token-value".toByteArray(Charsets.UTF_8)

    // 29. credential encrypted before persistence
    @Test
    fun `encrypt produces ciphertext that differs from the plaintext`() {
        val cipher = FakeCredentialCipher()
        val result = cipher.encrypt(plaintext) as CredentialEncryptionResult.Success

        assertFalse(result.payload.ciphertext.contentEquals(plaintext))
    }

    // 30. persisted record contains no plaintext token
    @Test
    fun `ciphertext never contains the plaintext as a byte subsequence`() {
        val cipher = FakeCredentialCipher()
        val result = cipher.encrypt(plaintext) as CredentialEncryptionResult.Success

        val ciphertextString = String(result.payload.ciphertext, Charsets.ISO_8859_1)
        val plaintextString = String(plaintext, Charsets.ISO_8859_1)
        assertFalse(ciphertextString.contains(plaintextString))
    }

    // 31. every encryption uses a distinct IV
    @Test
    fun `two encryptions of the same plaintext use different IVs and produce different ciphertext`() {
        val cipher = FakeCredentialCipher()
        val first = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val second = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload

        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    // 32. valid ciphertext decrypts correctly
    @Test
    fun `a freshly encrypted payload decrypts back to the exact original plaintext`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload

        val decrypted = cipher.decrypt(encrypted) as CredentialDecryptionResult.Success

        assertTrue(decrypted.plaintext.contentEquals(plaintext))
    }

    // 33. tampered ciphertext rejected
    @Test
    fun `a tampered ciphertext byte is rejected rather than decrypting to garbage`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() })

        val result = cipher.decrypt(tampered)

        assertEquals(CredentialDecryptionResult.InvalidCiphertext, result)
    }

    @Test
    fun `a tampered IV is rejected`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val tampered = encrypted.copy(iv = encrypted.iv.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() })

        val result = cipher.decrypt(tampered)

        assertEquals(CredentialDecryptionResult.InvalidCiphertext, result)
    }

    // 35. corrupt ciphertext produces explicit result (unrecognized format version)
    @Test
    fun `an unrecognized format version is rejected explicitly rather than attempting to decode it`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val futureFormat = encrypted.copy(formatVersion = encrypted.formatVersion + 1)

        val result = cipher.decrypt(futureFormat)

        assertEquals(CredentialDecryptionResult.InvalidCiphertext, result)
    }

    // missing key -> explicit result
    @Test
    fun `decrypting after the key is lost produces an explicit KeyMissing result`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        cipher.dropKey()

        val result = cipher.decrypt(encrypted)

        assertEquals(CredentialDecryptionResult.KeyMissing, result)
    }

    @Test
    fun `hasKey reflects key presence`() {
        val cipher = FakeCredentialCipher()
        assertTrue(cipher.hasKey())
        cipher.dropKey()
        assertFalse(cipher.hasKey())
    }

    // 36. no plaintext fallback — every failure path returns a distinct non-Success result, never plaintext
    @Test
    fun `every decrypt failure path returns a non-Success result, never a plaintext fallback`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload

        cipher.dropKey()
        val missingKeyResult = cipher.decrypt(encrypted)
        assertFalse(missingKeyResult is CredentialDecryptionResult.Success)

        val cipher2 = FakeCredentialCipher()
        val encrypted2 = (cipher2.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val tampered = encrypted2.copy(ciphertext = ByteArray(encrypted2.ciphertext.size))
        val tamperedResult = cipher2.decrypt(tampered)
        assertFalse(tamperedResult is CredentialDecryptionResult.Success)
    }

    @Test
    fun `EncryptedPayload toString never exposes ciphertext or IV bytes`() {
        val cipher = FakeCredentialCipher()
        val encrypted = (cipher.encrypt(plaintext) as CredentialEncryptionResult.Success).payload

        assertEquals("EncryptedPayload(formatVersion=1, ciphertext=<redacted>, iv=<redacted>)", encrypted.toString())
    }

    @Test
    fun `two independently generated ciphers produce different ciphertext for the same plaintext (independent keys)`() {
        val cipherA = FakeCredentialCipher()
        val cipherB = FakeCredentialCipher()

        val a = (cipherA.encrypt(plaintext) as CredentialEncryptionResult.Success).payload
        val b = (cipherB.encrypt(plaintext) as CredentialEncryptionResult.Success).payload

        assertNotEquals(a, b)
    }
}
