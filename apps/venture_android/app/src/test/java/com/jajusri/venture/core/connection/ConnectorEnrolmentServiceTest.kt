package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.discovery.ConnectorDiscoveryPort
import com.jajusri.venture.core.discovery.DiscoveredConnector
import com.jajusri.venture.core.discovery.FakeConnectorDiscoveryPort
import com.jajusri.venture.core.network.ConnectorBaseUrlProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorEnrolmentServiceTest {

    private val candidate = DiscoveredConnector(
        connectorId = "9c98ff3c-3b1c-4429-a1a9-4055ef4c95e4",
        name = "Front Desk",
        host = "192.168.29.34",
        port = 8080,
        apiVersion = "1.0.0",
        authRequired = false,
    )

    @Test
    fun `discover delegates straight to the discovery port with the requested timeout`() = runTest {
        val discovery = FakeConnectorDiscoveryPort(listOf(candidate))
        val service = serviceOf(discovery = discovery)

        val result = service.discover(6_000L)

        assertEquals(listOf(candidate), result)
        assertEquals(1, discovery.callCount)
        assertEquals(6_000L, discovery.lastRequestedTimeoutMs)
    }

    @Test
    fun `matching health response Connector ID pairs successfully`() = runTest {
        val paired = RecordingFakePairedConnectorLocalDataSource()
        val baseUrlStore = EnrolmentFakeBaseUrlLocalStore()
        val baseUrlProvider = EnrolmentFakeBaseUrlProvider()
        val service = serviceOf(
            healthProbe = EnrolmentFakeHealthProbe(reachable = true, connectorId = candidate.connectorId, connectorName = "Front Desk"),
            pairedConnectors = paired,
            baseUrlLocalStore = baseUrlStore,
            baseUrlProvider = baseUrlProvider,
        )

        val result = service.pair(candidate)

        assertTrue(result is EnrolmentResult.Paired)
        assertEquals(candidate.connectorId, (result as EnrolmentResult.Paired).connectorId)
    }

    @Test
    fun `mismatched health response Connector ID is rejected and never persisted`() = runTest {
        val paired = RecordingFakePairedConnectorLocalDataSource()
        val baseUrlStore = EnrolmentFakeBaseUrlLocalStore()
        val baseUrlProvider = EnrolmentFakeBaseUrlProvider()
        val service = serviceOf(
            healthProbe = EnrolmentFakeHealthProbe(reachable = true, connectorId = "a-completely-different-id", connectorName = "Impersonator"),
            pairedConnectors = paired,
            baseUrlLocalStore = baseUrlStore,
            baseUrlProvider = baseUrlProvider,
        )

        val result = service.pair(candidate)

        assertTrue(result is EnrolmentResult.IdentityMismatch)
        val mismatch = result as EnrolmentResult.IdentityMismatch
        assertEquals(candidate.connectorId, mismatch.advertisedConnectorId)
        assertEquals("a-completely-different-id", mismatch.actualConnectorId)
        assertEquals(0, paired.upsertCallCount)
        assertNull(baseUrlStore.saved)
        assertEquals(0, baseUrlProvider.updateCallCount)
    }

    @Test
    fun `unreachable endpoint is rejected and never persisted, cached business data untouched`() = runTest {
        val paired = RecordingFakePairedConnectorLocalDataSource()
        val baseUrlStore = EnrolmentFakeBaseUrlLocalStore()
        val baseUrlProvider = EnrolmentFakeBaseUrlProvider()
        val service = serviceOf(
            healthProbe = EnrolmentFakeHealthProbe(reachable = false, connectorId = null, connectorName = null),
            pairedConnectors = paired,
            baseUrlLocalStore = baseUrlStore,
            baseUrlProvider = baseUrlProvider,
        )

        val result = service.pair(candidate)

        assertEquals(EnrolmentResult.Unreachable, result)
        // The enrolment service has no dependency on ledger/stock/voucher storage at all — it
        // physically cannot clear cached business data, and this proves it doesn't even touch
        // the one thing it could (the paired-Connector record) on a failure path.
        assertEquals(0, paired.upsertCallCount)
        assertNull(baseUrlStore.saved)
    }

    @Test
    fun `pairing details persist atomically with the fields from the confirmed candidate`() = runTest {
        val paired = RecordingFakePairedConnectorLocalDataSource()
        val service = serviceOf(
            healthProbe = EnrolmentFakeHealthProbe(reachable = true, connectorId = candidate.connectorId, connectorName = "Front Desk"),
            pairedConnectors = paired,
            timeProvider = TimeProvider { 555_000L },
        )

        service.pair(candidate)

        assertEquals(1, paired.upsertCallCount)
        val stored = paired.rows.getValue(candidate.connectorId)
        assertEquals(candidate.connectorId, stored.connectorId)
        assertEquals("Front Desk", stored.friendlyName)
        assertEquals(candidate.host, stored.lastKnownHost)
        assertEquals(candidate.port, stored.lastKnownPort)
        assertEquals(555_000L, stored.lastConnectedAtEpochMillis)
        assertEquals(555_000L, stored.createdAtEpochMillis)
    }

    @Test
    fun `base URL provider is updated only after the paired record and local store both persist`() = runTest {
        val order = mutableListOf<String>()
        val paired = object : PairedConnectorLocalDataSource {
            override suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity? = null
            override suspend fun getPrimary(): PairedConnectorEntity? = null
            override suspend fun upsert(entity: PairedConnectorEntity) {
                order += "upsert"
            }
            override suspend fun updateLastKnownEndpoint(
                connectorId: String,
                host: String,
                port: Int,
                connectedAtEpochMillis: Long,
            ) = Unit
        }
        val baseUrlStore = object : ConnectorBaseUrlLocalStore {
            override val baseUrl: Flow<String> = MutableStateFlow("http://10.0.2.2:8080/").asStateFlow()
            override suspend fun save(normalizedBaseUrl: String) {
                order += "save"
            }
            override suspend fun read(): String = "http://10.0.2.2:8080/"
        }
        val baseUrlProvider = object : ConnectorBaseUrlProvider {
            override fun snapshot(): String = "http://10.0.2.2:8080/"
            override fun snapshotHttpUrl(): HttpUrl = snapshot().toHttpUrl()
            override fun observe(): Flow<String> = MutableStateFlow(snapshot()).asStateFlow()
            override fun updateInMemory(normalizedBaseUrl: String) {
                order += "updateInMemory"
            }
        }
        val service = serviceOf(
            healthProbe = EnrolmentFakeHealthProbe(reachable = true, connectorId = candidate.connectorId, connectorName = "Front Desk"),
            pairedConnectors = paired,
            baseUrlLocalStore = baseUrlStore,
            baseUrlProvider = baseUrlProvider,
        )

        val result = service.pair(candidate)

        assertTrue(result is EnrolmentResult.Paired)
        assertEquals(listOf("upsert", "save", "updateInMemory"), order)
    }

    private fun serviceOf(
        discovery: ConnectorDiscoveryPort = FakeConnectorDiscoveryPort(),
        healthProbe: ConnectorHealthProbe = EnrolmentFakeHealthProbe(reachable = true, connectorId = candidate.connectorId, connectorName = "Front Desk"),
        pairedConnectors: PairedConnectorLocalDataSource = RecordingFakePairedConnectorLocalDataSource(),
        baseUrlLocalStore: ConnectorBaseUrlLocalStore = EnrolmentFakeBaseUrlLocalStore(),
        baseUrlProvider: ConnectorBaseUrlProvider = EnrolmentFakeBaseUrlProvider(),
        timeProvider: TimeProvider = TimeProvider { 1L },
    ): ConnectorEnrolmentService = DefaultConnectorEnrolmentService(
        discoveryPort = discovery,
        healthProbe = healthProbe,
        pairedConnectors = pairedConnectors,
        baseUrlLocalStore = baseUrlLocalStore,
        baseUrlProvider = baseUrlProvider,
        timeProvider = timeProvider,
    )
}

private class EnrolmentFakeHealthProbe(
    private val reachable: Boolean,
    private val connectorId: String?,
    private val connectorName: String?,
) : ConnectorHealthProbe {
    override suspend fun probe(host: String, port: Int, timeoutMs: Long): ConnectorHealthProbeResult =
        ConnectorHealthProbeResult(reachable, connectorId, connectorName)
}

private class RecordingFakePairedConnectorLocalDataSource : PairedConnectorLocalDataSource {
    val rows = mutableMapOf<String, PairedConnectorEntity>()
    var upsertCallCount = 0
        private set

    override suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity? = rows[connectorId]
    override suspend fun getPrimary(): PairedConnectorEntity? = rows.values.maxByOrNull { it.lastConnectedAtEpochMillis }
    override suspend fun upsert(entity: PairedConnectorEntity) {
        upsertCallCount++
        rows[entity.connectorId] = entity
    }

    override suspend fun updateLastKnownEndpoint(
        connectorId: String,
        host: String,
        port: Int,
        connectedAtEpochMillis: Long,
    ) {
        val existing = rows[connectorId] ?: return
        rows[connectorId] = existing.copy(lastKnownHost = host, lastKnownPort = port, lastConnectedAtEpochMillis = connectedAtEpochMillis)
    }
}

private class EnrolmentFakeBaseUrlLocalStore : ConnectorBaseUrlLocalStore {
    private val flow = MutableStateFlow("http://10.0.2.2:8080/")
    var saved: String? = null
        private set

    override val baseUrl: Flow<String> = flow.asStateFlow()
    override suspend fun save(normalizedBaseUrl: String) {
        saved = normalizedBaseUrl
        flow.value = normalizedBaseUrl
    }
    override suspend fun read(): String = flow.value
}

private class EnrolmentFakeBaseUrlProvider : ConnectorBaseUrlProvider {
    private val flow = MutableStateFlow("http://10.0.2.2:8080/")
    var updateCallCount = 0
        private set

    override fun snapshot(): String = flow.value
    override fun snapshotHttpUrl(): HttpUrl = flow.value.toHttpUrl()
    override fun observe(): Flow<String> = flow.asStateFlow()
    override fun updateInMemory(normalizedBaseUrl: String) {
        updateCallCount++
        flow.value = normalizedBaseUrl
    }
}
