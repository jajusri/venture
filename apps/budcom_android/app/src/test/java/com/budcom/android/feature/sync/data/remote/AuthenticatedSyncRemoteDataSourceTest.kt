package com.budcom.android.feature.sync.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.connectorauth.domain.model.ConnectorTimeoutProfile
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.EncryptedPayload
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncTarget
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedSyncRemoteDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ===== Operation mapping =====

    @Test
    fun `startSync uses StartLedgerSync for Ledgers with the SYNC timeout profile`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(ledgerResultJson())))
        val dataSource = adapter(port)

        dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertEquals(AuthenticatedConnectorOperation.StartLedgerSync, port.lastOperation)
        assertEquals(ConnectorTimeoutProfile.SYNC, port.lastOperation?.timeoutProfile)
    }

    @Test
    fun `startSync uses StartStockItemSync for StockItems with the SYNC timeout profile`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(stockResultJson())))
        val dataSource = adapter(port)

        dataSource.startSync(SyncTarget.StockItems, SyncMode.Full)

        assertEquals(AuthenticatedConnectorOperation.StartStockItemSync, port.lastOperation)
        assertEquals(ConnectorTimeoutProfile.SYNC, port.lastOperation?.timeoutProfile)
    }

    @Test
    fun `startSync uses StartVoucherSync for Vouchers with the SYNC timeout profile`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(voucherResultJson())))
        val dataSource = adapter(port)

        dataSource.startSync(SyncTarget.Vouchers, SyncMode.Full)

        assertEquals(AuthenticatedConnectorOperation.StartVoucherSync, port.lastOperation)
        assertEquals(ConnectorTimeoutProfile.SYNC, port.lastOperation?.timeoutProfile)
    }

    @Test
    fun `cancelSync uses CancelLedgerSync and CancelStockItemSync with the STANDARD timeout profile`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"progress":{"status":"cancelled"}}""")))
        val dataSource = adapter(port)

        dataSource.cancelSync(SyncTarget.Ledgers)
        assertEquals(AuthenticatedConnectorOperation.CancelLedgerSync, port.lastOperation)
        assertEquals(ConnectorTimeoutProfile.STANDARD, port.lastOperation?.timeoutProfile)

        dataSource.cancelSync(SyncTarget.StockItems)
        assertEquals(AuthenticatedConnectorOperation.CancelStockItemSync, port.lastOperation)
    }

    @Test
    fun `cancelSync for Vouchers never calls the port and returns the established unavailable message`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"progress":{"status":"cancelled"}}""")))
        val dataSource = adapter(port)

        val result = dataSource.cancelSync(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals("Public voucher sync cancel is not available.", ((result as AppResult.Failure).error as AppError.Message).message)
        assertEquals(0, port.callCount)
    }

    @Test
    fun `fetchStatus uses LedgerSyncStatus and StockItemSyncStatus`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"progress":{"status":"idle"}}""")))
        val dataSource = adapter(port)

        dataSource.fetchStatus(SyncTarget.Ledgers)
        assertEquals(AuthenticatedConnectorOperation.LedgerSyncStatus, port.lastOperation)

        dataSource.fetchStatus(SyncTarget.StockItems)
        assertEquals(AuthenticatedConnectorOperation.StockItemSyncStatus, port.lastOperation)
    }

    @Test
    fun `fetchStatus for Vouchers never calls the port`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"progress":{"status":"idle"}}""")))
        val dataSource = adapter(port)

        val result = dataSource.fetchStatus(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, port.callCount)
    }

    @Test
    fun `fetchStatistics uses LedgerSyncStatistics and StockItemSyncStatistics`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"statistics":{}}""")))
        val dataSource = adapter(port)

        dataSource.fetchStatistics(SyncTarget.Ledgers)
        assertEquals(AuthenticatedConnectorOperation.LedgerSyncStatistics, port.lastOperation)

        dataSource.fetchStatistics(SyncTarget.StockItems)
        assertEquals(AuthenticatedConnectorOperation.StockItemSyncStatistics, port.lastOperation)
    }

    @Test
    fun `fetchStatistics for Vouchers never calls the port`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"statistics":{}}""")))
        val dataSource = adapter(port)

        val result = dataSource.fetchStatistics(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, port.callCount)
    }

    @Test
    fun `fetchRecentRuns uses LedgerSyncRuns and StockItemSyncRuns with the limit query parameter`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"runs":[]}""")))
        val dataSource = adapter(port)

        dataSource.fetchRecentRuns(SyncTarget.Ledgers, 7)
        val ledgerOp = port.lastOperation as AuthenticatedConnectorOperation.LedgerSyncRuns
        assertEquals(mapOf("limit" to "7"), ledgerOp.queryParams)

        dataSource.fetchRecentRuns(SyncTarget.StockItems, 3)
        val stockOp = port.lastOperation as AuthenticatedConnectorOperation.StockItemSyncRuns
        assertEquals(mapOf("limit" to "3"), stockOp.queryParams)
    }

    @Test
    fun `fetchRecentRuns for Vouchers never calls the port and returns an empty list`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"runs":[]}""")))
        val dataSource = adapter(port)

        val result = dataSource.fetchRecentRuns(SyncTarget.Vouchers, 5) as AppResult.Success

        assertTrue(result.value.isEmpty())
        assertEquals(0, port.callCount)
    }

    // ===== Decode / mapper reuse =====

    @Test
    fun `a successful ledger start response reuses the existing DTO and mapper`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(ledgerResultJson())))
        val dataSource = adapter(port)

        val result = dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full) as AppResult.Success
        val outcome = result.value as SyncOutcome.Succeeded
        assertEquals("r1", outcome.syncRunId)
        assertEquals(SyncTarget.Ledgers, outcome.target)
    }

    @Test
    fun `malformed success JSON returns a serialization failure`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("not json")))
        val dataSource = adapter(port)

        val result = dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a payload missing a required field returns a serialization failure`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("""{"syncRunId":"r1"}""")))
        val dataSource = adapter(port)

        val result = dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Serialization)
    }

    @Test
    fun `a non-Success outcome routes through the shared failure policy`() = runTest {
        val port = FakePort(AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val policy = PassthroughFailurePolicy()
        val dataSource = DefaultAuthenticatedSyncRemoteDataSource(port, policy, json)

        val result = dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertTrue(result is AppResult.Failure)
        assertEquals(1, policy.callCount)
        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), policy.lastResult)
    }

    @Test
    fun `the adapter exposes only the five sync methods — no run-detail, cache-clear, integrity or backup operation`() {
        val members = AuthenticatedSyncRemoteDataSource::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("startSync", "cancelSync", "fetchStatus", "fetchStatistics", "fetchRecentRuns"), members)
    }

    // ===== 401 credential-identity / replacement-race =====

    @Test
    fun `a 401 marks only the credential ID actually used by the rejected sync request`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-1") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(AuthenticatedConnectorResult.Unauthorized("cred-1"))
        val dataSource = DefaultAuthenticatedSyncRemoteDataSource(port, policy, json)

        val result = dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertTrue(result is AppResult.Failure)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, ((result as AppResult.Failure).error as AppError.Remote).code)
    }

    @Test
    fun `a stale credential A's 401 on a sync request does not invalidate replacement credential B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(AuthenticatedConnectorResult.Unauthorized("cred-A"))
        val dataSource = DefaultAuthenticatedSyncRemoteDataSource(port, policy, json)

        dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, store.record?.state)
    }

    @Test
    fun `current credential B's 401 on a sync request marks only B`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE, credentialId = "cred-B") }
        val vault = FakeSecureCredentialVault(backingStore = store)
        val policy = DefaultAuthenticatedRepositoryFailurePolicy(vault)
        val port = FakePort(AuthenticatedConnectorResult.Unauthorized("cred-B"))
        val dataSource = DefaultAuthenticatedSyncRemoteDataSource(port, policy, json)

        dataSource.startSync(SyncTarget.Ledgers, SyncMode.Full)

        assertEquals("cred-B", store.record?.credentialId)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, store.record?.state)
    }

    private fun adapter(port: FakePort) = DefaultAuthenticatedSyncRemoteDataSource(port, PassthroughFailurePolicy(), json)

    private fun ledgerResultJson(): String =
        """{"syncRunId":"r1","status":"completed","statistics":{},"progress":{"status":"completed"}}"""

    private fun stockResultJson(): String =
        """{"syncRunId":"r1","status":"completed","extractionCompleteness":"complete","statistics":{},"progress":{"status":"completed"}}"""

    private fun voucherResultJson(): String =
        """{"syncRunId":"r1","status":"completed","statistics":{},"progress":{"status":"completed"}}"""
}

private class FakePort(private val result: AuthenticatedConnectorResult) : AuthenticatedConnectorApiPort {
    var lastOperation: AuthenticatedConnectorOperation? = null
        private set
    var callCount = 0
        private set

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        callCount++
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
