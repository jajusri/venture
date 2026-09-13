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

class ConnectorConnectionResolverTest {

    @Test
    fun `no paired connector resolves offline without probing anything`() = runTest {
        val discovery = FakeConnectorDiscoveryPort()
        val resolver = resolverOf(paired = null, healthProbe = FakeHealthProbe(), discovery = discovery)

        val result = resolver.resolve()

        assertEquals(ConnectionResolution.Offline, result)
        assertEquals(0, discovery.callCount)
    }

    @Test
    fun `reachable last-known endpoint with matching id reconnects without discovery`() = runTest {
        val paired = pairedConnector(host = "192.168.1.20", port = 8080)
        val healthProbe = FakeHealthProbe(
            responses = mapOf(
                "192.168.1.20:8080" to ConnectorHealthProbeResult(true, paired.connectorId, "Front Desk"),
            ),
        )
        val discovery = FakeConnectorDiscoveryPort()
        val baseUrlProvider = FakeBaseUrlProvider()
        val baseUrlStore = FakeBaseUrlLocalStore()
        val resolver = resolverOf(paired, healthProbe, discovery, baseUrlProvider, baseUrlStore)

        val result = resolver.resolve() as ConnectionResolution.Connected

        assertEquals(paired.connectorId, result.connectorId)
        assertEquals("192.168.1.20", result.host)
        assertEquals(0, discovery.callCount)
        assertEquals("http://192.168.1.20:8080/", baseUrlProvider.snapshot())
        assertEquals("http://192.168.1.20:8080/", baseUrlStore.saved)
    }

    @Test
    fun `stale last-known endpoint falls back to discovery and reconnects on match`() = runTest {
        val paired = pairedConnector(host = "192.168.1.20", port = 8080)
        val healthProbe = FakeHealthProbe(
            responses = mapOf("192.168.1.20:8080" to ConnectorHealthProbeResult(false, null, null)),
        )
        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                DiscoveredConnector(
                    connectorId = paired.connectorId,
                    name = "Front Desk",
                    host = "192.168.1.45",
                    port = 8080,
                    apiVersion = "1.0.0",
                    authRequired = false,
                ),
            ),
        )
        val baseUrlProvider = FakeBaseUrlProvider()
        val baseUrlStore = FakeBaseUrlLocalStore()
        val resolver = resolverOf(paired, healthProbe, discovery, baseUrlProvider, baseUrlStore)

        val result = resolver.resolve() as ConnectionResolution.Connected

        assertEquals("192.168.1.45", result.host)
        assertEquals(1, discovery.callCount)
        assertEquals("http://192.168.1.45:8080/", baseUrlProvider.snapshot())
    }

    @Test
    fun `discovery result for a different connector id is never auto-paired`() = runTest {
        val paired = pairedConnector(host = "192.168.1.20", port = 8080)
        val healthProbe = FakeHealthProbe(
            responses = mapOf("192.168.1.20:8080" to ConnectorHealthProbeResult(false, null, null)),
        )
        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                DiscoveredConnector(
                    connectorId = "some-other-connector",
                    name = "Unrelated",
                    host = "192.168.1.99",
                    port = 8080,
                    apiVersion = "1.0.0",
                    authRequired = false,
                ),
            ),
        )
        val baseUrlProvider = FakeBaseUrlProvider()
        val resolver = resolverOf(paired, healthProbe, discovery, baseUrlProvider)

        val result = resolver.resolve()

        assertEquals(ConnectionResolution.Offline, result)
        assertNull(baseUrlProvider.updatedTo)
    }

    @Test
    fun `total failure never touches the base url provider`() = runTest {
        val paired = pairedConnector(host = "192.168.1.20", port = 8080)
        val healthProbe = FakeHealthProbe(
            responses = mapOf("192.168.1.20:8080" to ConnectorHealthProbeResult(false, null, null)),
        )
        val discovery = FakeConnectorDiscoveryPort(emptyList())
        val baseUrlProvider = FakeBaseUrlProvider()
        val resolver = resolverOf(paired, healthProbe, discovery, baseUrlProvider)

        val result = resolver.resolve()

        assertEquals(ConnectionResolution.Offline, result)
        assertTrue(baseUrlProvider.updateCallCount == 0)
    }

    private fun resolverOf(
        paired: PairedConnectorEntity?,
        healthProbe: ConnectorHealthProbe,
        discovery: ConnectorDiscoveryPort,
        baseUrlProvider: FakeBaseUrlProvider = FakeBaseUrlProvider(),
        baseUrlStore: FakeBaseUrlLocalStore = FakeBaseUrlLocalStore(),
    ): DefaultConnectorConnectionResolver = DefaultConnectorConnectionResolver(
        pairedConnectors = FakePairedConnectorLocalDataSource(paired),
        healthProbe = healthProbe,
        discoveryPort = discovery,
        baseUrlProvider = baseUrlProvider,
        baseUrlLocalStore = baseUrlStore,
        timeProvider = TimeProvider { 42L },
    )

    private fun pairedConnector(host: String, port: Int) = PairedConnectorEntity(
        connectorId = "cid-abc",
        friendlyName = "Front Desk",
        lastKnownHost = host,
        lastKnownPort = port,
        lastConnectedAtEpochMillis = 1L,
        createdAtEpochMillis = 1L,
    )
}

private class FakeHealthProbe(
    private val responses: Map<String, ConnectorHealthProbeResult> = emptyMap(),
) : ConnectorHealthProbe {
    override suspend fun probe(host: String, port: Int, timeoutMs: Long): ConnectorHealthProbeResult =
        responses["$host:$port"] ?: ConnectorHealthProbeResult(false, null, null)
}

private class FakePairedConnectorLocalDataSource(
    initial: PairedConnectorEntity?,
) : PairedConnectorLocalDataSource {
    private val rows = mutableMapOf<String, PairedConnectorEntity>()

    init {
        initial?.let { rows[it.connectorId] = it }
    }

    override suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity? = rows[connectorId]

    override suspend fun getPrimary(): PairedConnectorEntity? =
        rows.values.maxByOrNull { it.lastConnectedAtEpochMillis }

    override suspend fun upsert(entity: PairedConnectorEntity) {
        rows[entity.connectorId] = entity
    }

    override suspend fun updateLastKnownEndpoint(
        connectorId: String,
        host: String,
        port: Int,
        connectedAtEpochMillis: Long,
    ) {
        val existing = rows[connectorId] ?: return
        rows[connectorId] = existing.copy(
            lastKnownHost = host,
            lastKnownPort = port,
            lastConnectedAtEpochMillis = connectedAtEpochMillis,
        )
    }
}

private class FakeBaseUrlProvider : ConnectorBaseUrlProvider {
    private val flow = MutableStateFlow("http://10.0.2.2:8080/")
    var updatedTo: String? = null
        private set
    var updateCallCount: Int = 0
        private set

    override fun snapshot(): String = flow.value
    override fun snapshotHttpUrl(): HttpUrl = flow.value.toHttpUrl()
    override fun observe(): Flow<String> = flow.asStateFlow()
    override fun updateInMemory(normalizedBaseUrl: String) {
        updateCallCount++
        updatedTo = normalizedBaseUrl
        flow.value = normalizedBaseUrl
    }
}

private class FakeBaseUrlLocalStore : ConnectorBaseUrlLocalStore {
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
