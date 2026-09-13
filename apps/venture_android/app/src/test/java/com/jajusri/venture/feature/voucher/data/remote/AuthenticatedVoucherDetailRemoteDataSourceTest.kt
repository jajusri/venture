package com.jajusri.venture.feature.voucher.data.remote

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.jajusri.venture.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.jajusri.venture.core.connectorauth.domain.DefaultAuthenticatedRepositoryFailurePolicy
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialRecord
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.EncryptedPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedVoucherDetailRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fetchVoucherDetails uses exactly the GetVoucherById typed operation with the exact voucherId and companyId`() = runTest {
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, DetailPassthroughFailurePolicy(), json)

        dataSource.fetchVoucherDetails(companyId = "co-1", voucherId = "v-1")

        val operation = port.lastOperation as AuthenticatedConnectorOperation.GetVoucherById
        assertEquals("v-1", operation.voucherId)
        assertEquals("co-1", operation.companyId)
        assertEquals(mapOf("company" to "co-1"), operation.queryParams)
    }

    @Test
    fun `successful JSON maps through the existing DTOs and mapper`() = runTest {
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, DetailPassthroughFailurePolicy(), json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1") as AppResult.Success

        assertEquals("v-1", result.value.summary.identity.id)
        assertEquals("cached", result.value.narration)
    }

    @Test
    fun `a null voucher in a well-formed envelope maps to the existing NotFound semantics`() = runTest {
        val port = FakeDetailPort(
            result = AuthenticatedConnectorResult.Success(
                AuthenticatedConnectorResponsePayload("""{"schemaVersion":"1.0.0","data":{"companyId":"co-1","voucher":null}}"""),
            ),
        )
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, DetailPassthroughFailurePolicy(), json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1")

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error as AppError.Remote
        assertEquals(404, error.httpStatus)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun `malformed success JSON returns a serialization failure`() = runTest {
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("not json")))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, DetailPassthroughFailurePolicy(), json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a valid JSON payload missing the required data envelope returns a serialization failure`() = runTest {
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"schemaVersion":"1.0.0"}""")))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, DetailPassthroughFailurePolicy(), json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a non-Success outcome routes through the shared failure policy`() = runTest {
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val policy = DetailPassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, policy, json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1")

        assertTrue(result is AppResult.Failure)
        assertEquals(1, policy.callCount)
        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), policy.lastResult)
    }

    @Test
    fun `the adapter exposes only fetchVoucherDetails — no list, search, snapshot or sync operation`() {
        val members = AuthenticatedVoucherDetailRemoteDataSource::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("fetchVoucherDetails"), members)
    }

    // ===== 401 credential-identity / replacement-race, routed through the real shared policy =====

    @Test
    fun `a 401 marks only the credential ID actually used by the rejected voucher-detail request`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleDetailRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, policy, json)

        val result = dataSource.fetchVoucherDetails("co-1", "v-1")

        assertTrue(result is AppResult.Failure)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, ((result as AppResult.Failure).error as AppError.Remote).code)
    }

    @Test
    fun `a stale credential A's 401 on a voucher-detail request does not invalidate replacement credential B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleDetailRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Unauthorized("cred-A"))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, policy, json)

        dataSource.fetchVoucherDetails("co-1", "v-1")

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `current credential B's 401 on a voucher-detail request marks only B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleDetailRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakeDetailPort(result = AuthenticatedConnectorResult.Unauthorized("cred-B"))
        val dataSource = DefaultAuthenticatedVoucherDetailRemoteDataSource(port, policy, json)

        dataSource.fetchVoucherDetails("co-1", "v-1")

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    private fun sampleJson(): String =
        """{"schemaVersion":"1.0.0","data":{"companyId":"co-1","voucher":{"id":"v-1","date":"2026-07-01","type":"Sales",""" +
            """"status":"active","dataQuality":"complete","narration":"cached"}}}"""
}

private class FakeDetailPort(private val result: AuthenticatedConnectorResult) : AuthenticatedConnectorApiPort {
    var lastOperation: AuthenticatedConnectorOperation? = null
        private set

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        lastOperation = operation
        return result
    }
}

private class DetailPassthroughFailurePolicy : AuthenticatedRepositoryFailurePolicy {
    var callCount = 0
        private set
    var lastResult: AuthenticatedConnectorResult? = null
        private set

    override suspend fun mapFailure(result: AuthenticatedConnectorResult): AppError {
        callCount++
        lastResult = result
        return AppError.Message("mapped: $result")
    }
}

private fun sampleDetailRecord(state: SecurePairingCredentialState, credentialId: String) = SecurePairingCredentialRecord(
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
