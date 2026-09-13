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
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRange
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSort
import com.jajusri.venture.feature.voucher.domain.model.VoucherSortDirection
import com.jajusri.venture.feature.voucher.domain.model.VoucherSortField
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedVoucherListRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fetchVouchers uses exactly the ListVouchers typed operation with query params mirroring the legacy call`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, PassthroughFailurePolicy(), json)

        dataSource.fetchVouchers(
            VoucherQuery(
                companyId = "co-1",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
                searchText = "acme",
                voucherType = "Sales",
                voucherNumber = "S-1",
                partyName = "Acme Ltd",
                page = 2,
                pageSize = 25,
                sort = VoucherSort(VoucherSortField.Amount, VoucherSortDirection.Desc),
            ),
        )

        val operation = port.lastOperation as AuthenticatedConnectorOperation.ListVouchers
        assertEquals(
            mapOf(
                "company" to "co-1",
                "from" to "2026-07-01",
                "to" to "2026-07-27",
                "page" to "2",
                "pageSize" to "25",
                "sort" to "-amount",
                "q" to "acme",
                "voucherType" to "Sales",
                "voucherNumber" to "S-1",
                "partyName" to "Acme Ltd",
            ),
            operation.queryParams,
        )
    }

    @Test
    fun `fetchVouchers omits optional params entirely when blank, matching legacy Retrofit omission`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, PassthroughFailurePolicy(), json)

        dataSource.fetchVouchers(VoucherQuery(companyId = "co-1", dateRange = VoucherDateRange("2026-07-01", "2026-07-27")))

        val operation = port.lastOperation as AuthenticatedConnectorOperation.ListVouchers
        assertFalse(operation.queryParams.containsKey("q"))
        assertFalse(operation.queryParams.containsKey("voucherType"))
        assertFalse(operation.queryParams.containsKey("voucherNumber"))
        assertFalse(operation.queryParams.containsKey("partyName"))
        // company/from/to/page/pageSize/sort are always present, matching the legacy call.
        assertEquals(setOf("company", "from", "to", "page", "pageSize", "sort"), operation.queryParams.keys)
    }

    @Test
    fun `successful JSON maps through the existing DTOs and mappers`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(sampleJson())))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchVouchers(sampleQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals("v-1", result.value.items.single().identity.id)
        assertEquals(1, result.value.totalPages)
    }

    @Test
    fun `malformed success JSON returns a serialization failure`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("not json")))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchVouchers(sampleQuery())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a valid JSON payload missing the required data envelope returns a serialization failure`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"schemaVersion":"1.0.0"}""")))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, PassthroughFailurePolicy(), json)

        val result = dataSource.fetchVouchers(sampleQuery())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a non-Success outcome routes through the shared failure policy`() = runTest {
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val policy = PassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, policy, json)

        val result = dataSource.fetchVouchers(sampleQuery())

        assertTrue(result is AppResult.Failure)
        assertEquals(1, policy.callCount)
        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), policy.lastResult)
    }

    @Test
    fun `the adapter exposes only fetchVouchers — no detail, search, snapshot or sync operation`() {
        val members = AuthenticatedVoucherListRemoteDataSource::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("fetchVouchers"), members)
    }

    // ===== 401 credential-identity / replacement-race, routed through the real shared policy =====

    @Test
    fun `a 401 marks only the credential ID actually used by the rejected voucher-list request`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, policy, json)

        val result = dataSource.fetchVouchers(sampleQuery())

        assertTrue(result is AppResult.Failure)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, ((result as AppResult.Failure).error as AppError.Remote).code)
    }

    @Test
    fun `a stale credential A's 401 on a voucher-list request does not invalidate replacement credential B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-A"))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, policy, json)

        dataSource.fetchVouchers(sampleQuery())

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `current credential B's 401 on a voucher-list request marks only B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(result = AuthenticatedConnectorResult.Unauthorized("cred-B"))
        val dataSource = DefaultAuthenticatedVoucherListRemoteDataSource(port, policy, json)

        dataSource.fetchVouchers(sampleQuery())

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    private fun sampleQuery() = VoucherQuery(companyId = "co-1", dateRange = VoucherDateRange("2026-07-01", "2026-07-27"))

    private fun sampleJson(): String =
        """{"schemaVersion":"1.0.0","data":{"companyId":"co-1","items":[""" +
            """{"id":"v-1","date":"2026-07-01","type":"Sales","status":"active","dataQuality":"complete"}""" +
            """],"pagination":{"page":1,"pageSize":50,"totalItems":1,"totalPages":1}}}"""
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
