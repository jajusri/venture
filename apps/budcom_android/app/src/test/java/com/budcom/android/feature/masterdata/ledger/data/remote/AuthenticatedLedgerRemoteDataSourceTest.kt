package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.EncryptedPayload
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerSortBy
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerSortDirection
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedLedgerRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fetchLedgers uses exactly the GetLedgers typed operation with query params mirroring the legacy call`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, PassthroughFailurePolicy(), json)

        dataSource.fetchLedgers(
            LedgerQuery(text = "cash", page = 2, pageSize = 25, sortBy = LedgerSortBy.ParentGroup, sortDirection = LedgerSortDirection.Desc),
        )

        val operation = port.lastOperation as AuthenticatedConnectorOperation.GetLedgers
        assertEquals(
            mapOf("query" to "cash", "page" to "2", "pageSize" to "25", "sortBy" to "parentGroup", "sortDirection" to "desc"),
            operation.queryParams,
        )
    }

    @Test
    fun `fetchLedgers omits the query param entirely when the search text is blank`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, PassthroughFailurePolicy(), json)

        dataSource.fetchLedgers(LedgerQuery(text = null))

        val operation = port.lastOperation as AuthenticatedConnectorOperation.GetLedgers
        assertFalse(operation.queryParams.containsKey("query"))
    }

    @Test
    fun `successful JSON maps through the existing DTOs and mappers`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchLedgers(LedgerQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals("guid:cash", result.value.items.single().id)
        assertEquals(1, result.value.totalPages)
    }

    @Test
    fun `malformed success JSON returns a serialization failure`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("not json")))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchLedgers(LedgerQuery())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a valid JSON payload missing the required pagination envelope returns a serialization failure`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"items":[]}""")))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchLedgers(LedgerQuery())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a non-Success outcome routes through the shared failure policy`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val policy = PassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, policy, json)

        val result = dataSource.fetchLedgers(LedgerQuery())

        assertTrue(result is AppResult.Failure)
        assertEquals(1, policy.callCount)
        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), policy.lastResult)
    }

    @Test
    fun `the adapter exposes only fetchLedgers — no detail, group, stock, voucher or sync operation`() {
        val members = AuthenticatedLedgerRemoteDataSource::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("fetchLedgers"), members)
    }

    // ===== 401 credential-identity / replacement-race, routed through the real shared policy =====

    @Test
    fun `a 401 marks only the credential ID actually used by the rejected ledger request`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, policy, json)

        val result = dataSource.fetchLedgers(LedgerQuery())

        assertTrue(result is AppResult.Failure)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, ((result as AppResult.Failure).error as AppError.Remote).code)
    }

    @Test
    fun `a stale credential A's 401 on a ledger request does not invalidate replacement credential B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        // Simulates a request that was actually sent using now-superseded credential A.
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-A"))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, policy, json)

        dataSource.fetchLedgers(LedgerQuery())

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `current credential B's 401 on a ledger request marks only B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-B"))
        val dataSource = DefaultAuthenticatedLedgerRemoteDataSource(port, policy, json)

        dataSource.fetchLedgers(LedgerQuery())

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    private fun sampleJson(): String =
        """{"schemaVersion":"1.0.0","dataFreshnessAt":"2026-01-01T00:00:00Z","items":[""" +
            """{"id":"guid:cash","name":"Cash","status":"active","dataQuality":"complete","syncedAt":"2026-01-01T00:00:00Z"}""" +
            """],"pagination":{"page":1,"pageSize":50,"totalItems":1,"totalPages":1}}"""
}

private class FakePort(private val result: AuthenticatedConnectorResult) : AuthenticatedConnectorApiPort {
    var lastOperation: AuthenticatedConnectorOperation? = null
        private set

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        lastOperation = operation
        return result
    }
}

private class PassthroughFailurePolicy : AuthenticatedRepositoryFailurePolicy {
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

private fun sampleRecord(state: SecurePairingCredentialState, credentialId: String) = SecurePairingCredentialRecord(
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
