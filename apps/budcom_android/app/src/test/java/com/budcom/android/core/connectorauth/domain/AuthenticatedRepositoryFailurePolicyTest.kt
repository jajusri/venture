package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.common.AppError
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.EncryptedPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedRepositoryFailurePolicyTest {

    @Test
    fun `Unauthorized marks the credential it names RE_PAIR_REQUIRED and maps to a re-pair error`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        val error = policy.mapFailure(AuthenticatedConnectorResult.Unauthorized("cred-1"))

        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
        assertTrue(error is AppError.Remote)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, (error as AppError.Remote).code)
        assertEquals(401, error.httpStatus)
    }

    @Test
    fun `Unauthorized does not mutate the vault when no credential is stored`() = runTest {
        val vault = FakeSecureCredentialVault()
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        policy.mapFailure(AuthenticatedConnectorResult.Unauthorized("cred-1"))

        assertNull(vault.read())
    }

    @Test
    fun `Unauthorized does not mutate an already RE_PAIR_REQUIRED credential`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.RE_PAIR_REQUIRED, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        policy.mapFailure(AuthenticatedConnectorResult.Unauthorized("cred-1"))

        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    // ===== Credential-replacement race safety (Phase 3) =====
    //
    // Scenario: an in-flight request was sent using credential A's bearer token. Before its 401
    // response is handled, the device re-pairs and the vault now holds a different credential B
    // (a fresh ACTIVE record with a different credentialId). A's rejection must never touch B.

    @Test
    fun `a 401 for a replaced credential A does not touch the current credential B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        // The rejected request was sent using the now-superseded credential A.
        val error = policy.mapFailure(AuthenticatedConnectorResult.Unauthorized("cred-A"))

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
        // The caller is still told to re-pair — credential A truly was rejected — but B's own
        // state on disk is untouched by A's stale rejection.
        assertTrue(error is AppError.Remote)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, (error as AppError.Remote).code)
    }

    @Test
    fun `a 401 for the current credential B marks only B RE_PAIR_REQUIRED`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        policy.mapFailure(AuthenticatedConnectorResult.Unauthorized("cred-B"))

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    @Test
    fun `a 403 for the current credential B leaves B ACTIVE`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        policy.mapFailure(AuthenticatedConnectorResult.Forbidden)

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `Forbidden maps to a 403 access-denied error without touching the vault`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE) }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)

        val error = policy.mapFailure(AuthenticatedConnectorResult.Forbidden) as AppError.Remote

        assertEquals(403, error.httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, error.code)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `local vault states map to a re-pair error with a null http status`() = runTest {
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(FakeSecureCredentialVault())

        listOf(
            AuthenticatedConnectorResult.Unpaired,
            AuthenticatedConnectorResult.PendingVerification,
            AuthenticatedConnectorResult.RePairRequired,
            AuthenticatedConnectorResult.CredentialUnavailable,
        ).forEach { result ->
            val error = policy.mapFailure(result) as AppError.Remote
            assertNull(error.httpStatus)
            assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, error.code)
        }
    }

    @Test
    fun `ValidationFailure carries the sanitized code as a 400`() = runTest {
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(FakeSecureCredentialVault())

        val error = policy.mapFailure(AuthenticatedConnectorResult.ValidationFailure("COMPANY_NOT_FOUND")) as AppError.Remote

        assertEquals(400, error.httpStatus)
        assertEquals("COMPANY_NOT_FOUND", error.code)
    }

    @Test
    fun `ValidationFailure with isNoCompanySelected maps to the NO_COMPANY_SELECTED sentinel code, not the sanitized code`() = runTest {
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(FakeSecureCredentialVault())

        val error = policy.mapFailure(
            AuthenticatedConnectorResult.ValidationFailure(sanitizedCode = null, isNoCompanySelected = true),
        ) as AppError.Remote

        assertEquals(400, error.httpStatus)
        assertEquals(AUTHENTICATED_NO_COMPANY_SELECTED_CODE, error.code)
    }

    @Test
    fun `SessionExpired maps to a typed 410 without changing secure pairing state`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE) }
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(FakeSecureCredentialVault(backingStore = store))

        val error = policy.mapFailure(AuthenticatedConnectorResult.SessionExpired) as AppError.Remote

        assertEquals(410, error.httpStatus)
        assertEquals(AUTHENTICATED_SESSION_EXPIRED_CODE, error.code)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `remaining outcomes map to their expected AppError types`() = runTest {
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(FakeSecureCredentialVault())

        assertEquals(404, (policy.mapFailure(AuthenticatedConnectorResult.NotFound) as AppError.Remote).httpStatus)
        assertEquals(409, (policy.mapFailure(AuthenticatedConnectorResult.Conflict) as AppError.Remote).httpStatus)
        assertEquals(429, (policy.mapFailure(AuthenticatedConnectorResult.RateLimited) as AppError.Remote).httpStatus)
        assertEquals(500, (policy.mapFailure(AuthenticatedConnectorResult.ServerFailure(500)) as AppError.Remote).httpStatus)
        assertTrue(policy.mapFailure(AuthenticatedConnectorResult.TransportFailure) is AppError.Offline)
        assertTrue(policy.mapFailure(AuthenticatedConnectorResult.MalformedResponse) is AppError.Serialization)
        assertTrue(policy.mapFailure(AuthenticatedConnectorResult.Cancelled) is AppError.Message)
    }
}

private fun sampleRecord(state: SecurePairingCredentialState, credentialId: String = "cred-1") = SecurePairingCredentialRecord(
    credentialId = credentialId,
    deviceId = "device-1",
    encryptedCredential = EncryptedPayload(ciphertext = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6), formatVersion = 1),
    endpoint = TrustedConnectorEndpoint(
        connectorId = "connector-1",
        connectorName = "Test Connector",
        host = "192.168.1.10",
        securePort = 8443,
        transportFingerprint = "sha256/AAAA",
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    ),
    createdAtEpochMillis = 1_000L,
    lastVerifiedAtEpochMillis = null,
    state = state,
)
