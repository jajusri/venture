package com.jajusri.venture.core.connectorauth.domain

import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.FakeCredentialCipher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun testEndpoint(connectorId: String = "connector-abc") = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
    connectorId = connectorId,
    connectorName = "Front Desk",
    host = "10.100.141.234",
    securePort = 8443,
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
)

class AuthenticatedConnectorContextProviderTest {

    // 1. empty vault returns Unpaired
    @Test
    fun `an empty vault resolves to Unpaired`() = runTest {
        val vault = FakeSecureCredentialVault()
        val provider = DefaultAuthenticatedConnectorContextProvider(vault)

        assertEquals(AuthenticatedConnectorContextResolution.Unpaired, provider.resolve())
    }

    // 2. pending record returns PendingVerification
    @Test
    fun `a PENDING_VERIFICATION record resolves to PendingVerification`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertEquals(AuthenticatedConnectorContextResolution.PendingVerification, resolution)
    }

    // 3. re-pair-required record returns RePairRequired
    @Test
    fun `a RE_PAIR_REQUIRED record resolves to RePairRequired`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        vault.markRePairRequired("cred-1")

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertEquals(AuthenticatedConnectorContextResolution.RePairRequired, resolution)
    }

    // 4. ACTIVE record plus decrypt success returns Ready
    @Test
    fun `an ACTIVE record with a decryptable credential resolves to Ready`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertTrue(resolution is AuthenticatedConnectorContextResolution.Ready)
        val ready = resolution as AuthenticatedConnectorContextResolution.Ready
        assertEquals("connector-abc", ready.context.endpoint.connectorId)
        assertEquals("device-1", ready.context.logicalDeviceId)
        assertEquals("cred-1", ready.context.credentialId)
        assertEquals("Bearer raw-token", ready.context.bearerHeaderValue())
    }

    // 5. missing Keystore key returns CredentialUnavailable
    @Test
    fun `an ACTIVE record whose key is missing resolves to CredentialUnavailable`() = runTest {
        val cipher = FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        cipher.dropKey()

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertEquals(AuthenticatedConnectorContextResolution.CredentialUnavailable, resolution)
    }

    // 6. invalid ciphertext returns CredentialUnavailable
    @Test
    fun `an ACTIVE record with tampered ciphertext resolves to CredentialUnavailable`() = runTest {
        val backingStore = InMemoryVaultBackingStore()
        val cipher = FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher, backingStore = backingStore)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        val tamperedRecord = requireNotNull(backingStore.record)
        backingStore.record = tamperedRecord.copy(
            encryptedCredential = tamperedRecord.encryptedCredential.copy(
                ciphertext = tamperedRecord.encryptedCredential.ciphertext.also { it[0] = it[0].inc() },
            ),
        )

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertEquals(AuthenticatedConnectorContextResolution.CredentialUnavailable, resolution)
    }

    // 7 & 8. no network call and no vault mutation occurs while resolving context
    @Test
    fun `resolving context never mutates the vault and reads it exactly once per call`() = runTest {
        val vault = CountingVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        vault.resetCounts()

        DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertEquals(1, vault.readCount)
        assertEquals(1, vault.readDecryptedCount)
        assertEquals(0, vault.mutationCount)
    }

    // 9, 10, 11, 12. context is reloaded for every request; credential/state changes observed next call
    @Test
    fun `credential replacement between two calls is observed by the next resolve`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "first-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        val provider = DefaultAuthenticatedConnectorContextProvider(vault)
        val first = provider.resolve() as AuthenticatedConnectorContextResolution.Ready
        assertEquals("Bearer first-token", first.context.bearerHeaderValue())

        vault.storePendingVerification("cred-2", "device-1", "second-token", testEndpoint(), 3_000L)
        vault.markActive("cred-2", 4_000L)
        val second = provider.resolve() as AuthenticatedConnectorContextResolution.Ready

        assertEquals("Bearer second-token", second.context.bearerHeaderValue())
    }

    @Test
    fun `revocation between two calls is observed by the next resolve`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)
        val provider = DefaultAuthenticatedConnectorContextProvider(vault)
        assertTrue(provider.resolve() is AuthenticatedConnectorContextResolution.Ready)

        vault.markRePairRequired("cred-1")

        assertEquals(AuthenticatedConnectorContextResolution.RePairRequired, provider.resolve())
    }

    // 13. context toString is redacted
    @Test
    fun `a Ready resolution's toString never includes the credential`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "super-secret-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)

        val resolution = DefaultAuthenticatedConnectorContextProvider(vault).resolve()

        assertFalse(resolution.toString().contains("super-secret-token"))
        val ready = resolution as AuthenticatedConnectorContextResolution.Ready
        assertFalse(ready.context.toString().contains("super-secret-token"))
    }

    // 14. credential wrapper toString is redacted
    @Test
    fun `the bearer credential wrapper's toString never includes the raw value`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "super-secret-token", testEndpoint(), 1_000L)
        vault.markActive("cred-1", 2_000L)

        val ready = DefaultAuthenticatedConnectorContextProvider(vault).resolve() as AuthenticatedConnectorContextResolution.Ready
        val redactedField = ready.context.javaClass.getDeclaredField("bearerCredential").apply { isAccessible = true }
        val wrapperText = requireNotNull(redactedField.get(ready.context)).toString()

        assertFalse(wrapperText.contains("super-secret-token"))
        assertTrue(wrapperText.contains("redacted"))
    }
}

/** Wraps [FakeSecureCredentialVault] to count reads/mutations without changing its behavior. */
private class CountingVault(
    private val delegate: FakeSecureCredentialVault = FakeSecureCredentialVault(),
) : com.jajusri.venture.core.pairing.data.local.SecureCredentialVault by delegate {
    var readCount = 0
        private set
    var readDecryptedCount = 0
        private set
    var mutationCount = 0
        private set

    fun resetCounts() {
        readCount = 0
        readDecryptedCount = 0
        mutationCount = 0
    }

    override suspend fun read() = delegate.read().also { readCount++ }
    override suspend fun readDecryptedCredential() = delegate.readDecryptedCredential().also { readDecryptedCount++ }

    override suspend fun storePendingVerification(
        credentialId: String,
        deviceId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        createdAtEpochMillis: Long,
    ) = delegate.storePendingVerification(credentialId, deviceId, rawCredential, endpoint, createdAtEpochMillis).also { mutationCount++ }

    override suspend fun markActive(credentialId: String, verifiedAtEpochMillis: Long) =
        delegate.markActive(credentialId, verifiedAtEpochMillis).also { mutationCount++ }

    override suspend fun markRePairRequired(credentialId: String) =
        delegate.markRePairRequired(credentialId).also { mutationCount++ }

    override suspend fun clear() {
        delegate.clear()
        mutationCount++
    }
}
