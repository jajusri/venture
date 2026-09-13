package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.network.DynamicBaseUrlInterceptor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorConnectionOrchestratorTest {

    @Test
    fun `unpaired install migrates the raw URL then resolves through the new paired record`() = runTest {
        val local = OrchestratorLocalFake()
        val migration = OrchestratorMigrationFake(MigrationOutcome.Migrated("cid-1"))
        val resolution = ConnectionResolution.Connected("cid-1", "Front Desk", "10.0.2.2", 8080)
        val resolver = OrchestratorResolverFake(resolution)
        val orchestrator = DefaultConnectorConnectionOrchestrator(migration, resolver, local)

        val result = orchestrator.ensureConnected()

        assertEquals(1, migration.callCount)
        assertEquals(1, resolver.callCount)
        assertEquals(resolution, result)
    }

    @Test
    fun `already paired install skips migration and only resolves`() = runTest {
        val paired = pairedConnector()
        val local = OrchestratorLocalFake(paired)
        val migration = OrchestratorMigrationFake(MigrationOutcome.AlreadyPaired(paired.connectorId))
        val resolver = OrchestratorResolverFake(
            ConnectionResolution.Connected(paired.connectorId, paired.friendlyName, paired.lastKnownHost, paired.lastKnownPort),
        )
        val orchestrator = DefaultConnectorConnectionOrchestrator(migration, resolver, local)

        orchestrator.ensureConnected()

        assertEquals(0, migration.callCount)
        assertEquals(1, resolver.callCount)
    }

    @Test
    fun `migration never re-runs once a Connector has ever been paired, even after a failed resolve`() = runTest {
        val paired = pairedConnector()
        val local = OrchestratorLocalFake(paired)
        val migration = OrchestratorMigrationFake(MigrationOutcome.Migrated("should-not-run-again"))
        val resolver = OrchestratorResolverFake(ConnectionResolution.Offline)
        val orchestrator = DefaultConnectorConnectionOrchestrator(migration, resolver, local)

        orchestrator.ensureConnected()
        orchestrator.ensureConnected()

        assertEquals(0, migration.callCount)
        assertEquals(2, resolver.callCount)
    }

    @Test
    fun `discovery failure returns Offline and leaves the paired record untouched`() = runTest {
        val paired = pairedConnector()
        val local = OrchestratorLocalFake(paired)
        val resolver = OrchestratorResolverFake(ConnectionResolution.Offline)
        val orchestrator = DefaultConnectorConnectionOrchestrator(
            OrchestratorMigrationFake(MigrationOutcome.Unreachable),
            resolver,
            local,
        )

        val result = orchestrator.ensureConnected()

        assertEquals(ConnectionResolution.Offline, result)
        assertEquals(paired, local.rows[paired.connectorId])
    }

    @Test
    fun `resolved pairing survives a simulated app restart`() = runTest {
        val sharedStore = OrchestratorLocalFake()
        val migration = OrchestratorMigrationFake(MigrationOutcome.Migrated("cid-1"), store = sharedStore)
        val resolver = OrchestratorResolverFake(
            ConnectionResolution.Connected("cid-1", "Front Desk", "10.0.2.2", 8080),
        )

        val beforeRestart = DefaultConnectorConnectionOrchestrator(migration, resolver, sharedStore)
        beforeRestart.ensureConnected()
        assertEquals(1, migration.callCount)

        // A fresh orchestrator instance simulates process death; sharedStore simulates the
        // Room database surviving the restart intact.
        val afterRestart = DefaultConnectorConnectionOrchestrator(migration, resolver, sharedStore)
        afterRestart.ensureConnected()

        assertEquals(1, migration.callCount) // never re-ran after restart
        assertEquals(2, resolver.callCount)
    }

    @Test
    fun `concurrent ensureConnected calls never run migration more than once`() = runTest {
        val local = OrchestratorLocalFake()
        val migration = OrchestratorMigrationFake(MigrationOutcome.Migrated("cid-1"), store = local)
        val resolver = OrchestratorResolverFake(
            ConnectionResolution.Connected("cid-1", "Front Desk", "10.0.2.2", 8080),
        )
        val orchestrator = DefaultConnectorConnectionOrchestrator(migration, resolver, local)

        val first = async { orchestrator.ensureConnected() }
        val second = async { orchestrator.ensureConnected() }
        awaitAll(first, second)

        assertEquals(1, migration.callCount)
        assertEquals(2, resolver.callCount)
    }

    @Test
    fun `a different Connector ID discovered elsewhere never overrides the paired identity`() = runTest {
        val paired = pairedConnector()
        val local = OrchestratorLocalFake(paired)
        // The resolver itself is the authority on connectorId matching; the orchestrator
        // faithfully returns whatever it decides — here, Offline, because the resolver never
        // auto-pairs with a non-matching discovered Connector.
        val resolver = OrchestratorResolverFake(ConnectionResolution.Offline)
        val orchestrator = DefaultConnectorConnectionOrchestrator(
            OrchestratorMigrationFake(MigrationOutcome.AlreadyPaired(paired.connectorId)),
            resolver,
            local,
        )

        val result = orchestrator.ensureConnected()

        assertEquals(ConnectionResolution.Offline, result)
        assertEquals(paired.connectorId, local.rows.keys.single())
    }

    @Test
    fun `resolved endpoint becomes the live base URL every Retrofit request actually hits`() = runTest {
        // Real resolver + real ConnectorBaseUrlProvider + real DynamicBaseUrlInterceptor —
        // proving the exact same object graph Company/Session/Voucher Retrofit APIs share,
        // not just that the fake orchestrator returned the right value.
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        server.start()
        try {
            val paired = PairedConnectorEntity(
                connectorId = "cid-1",
                friendlyName = "Front Desk",
                lastKnownHost = server.hostName,
                lastKnownPort = server.port,
                lastConnectedAtEpochMillis = 1L,
                createdAtEpochMillis = 1L,
            )
            val local = OrchestratorLocalFake(paired)
            val healthProbe = object : ConnectorHealthProbe {
                override suspend fun probe(host: String, port: Int, timeoutMs: Long) =
                    ConnectorHealthProbeResult(reachable = true, connectorId = "cid-1", connectorName = "Front Desk")
            }
            val provider = com.jajusri.venture.core.network.DefaultConnectorBaseUrlProvider()
            val baseUrlStore = object : com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore {
                override val baseUrl = kotlinx.coroutines.flow.flowOf("http://10.0.2.2:8080/")
                override suspend fun save(normalizedBaseUrl: String) = Unit
                override suspend fun read(): String = "http://10.0.2.2:8080/"
            }
            val realResolver = DefaultConnectorConnectionResolver(
                pairedConnectors = local,
                healthProbe = healthProbe,
                discoveryPort = com.jajusri.venture.core.discovery.FakeConnectorDiscoveryPort(),
                baseUrlProvider = provider,
                baseUrlLocalStore = baseUrlStore,
                timeProvider = com.jajusri.venture.core.util.TimeProvider { 99L },
            )
            val orchestrator = DefaultConnectorConnectionOrchestrator(
                OrchestratorMigrationFake(MigrationOutcome.AlreadyPaired("cid-1")),
                realResolver,
                local,
            )

            val result = orchestrator.ensureConnected()
            assertTrue(result is ConnectionResolution.Connected)

            val client = OkHttpClient.Builder().addInterceptor(DynamicBaseUrlInterceptor(provider)).build()
            val response = client.newCall(Request.Builder().url("http://placeholder-never-used/health").build()).execute()

            assertEquals(200, response.code)
            assertEquals("/health", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    private fun pairedConnector() = PairedConnectorEntity(
        connectorId = "cid-1",
        friendlyName = "Front Desk",
        lastKnownHost = "192.168.1.20",
        lastKnownPort = 8080,
        lastConnectedAtEpochMillis = 1L,
        createdAtEpochMillis = 1L,
    )
}

/**
 * Mirrors [DefaultExistingUrlMigrationService]'s one real side effect (inserting a paired
 * row) when [store] is supplied, so composition tests can prove the orchestrator's "migrate
 * only while unpaired" guard against a store that behaves like the real one.
 */
private class OrchestratorMigrationFake(
    private val outcome: MigrationOutcome,
    private val store: OrchestratorLocalFake? = null,
) : ExistingUrlMigrationService {
    var callCount = 0
        private set

    override suspend fun migrateIfNeeded(): MigrationOutcome {
        callCount++
        if (outcome is MigrationOutcome.Migrated) {
            store?.upsert(
                PairedConnectorEntity(
                    connectorId = outcome.connectorId,
                    friendlyName = "Front Desk",
                    lastKnownHost = "10.0.2.2",
                    lastKnownPort = 8080,
                    lastConnectedAtEpochMillis = 1L,
                    createdAtEpochMillis = 1L,
                ),
            )
        }
        return outcome
    }
}

private class OrchestratorResolverFake(private val resolution: ConnectionResolution) : ConnectorConnectionResolver {
    var callCount = 0
        private set

    override suspend fun resolve(): ConnectionResolution {
        callCount++
        return resolution
    }
}

private class OrchestratorLocalFake(initial: PairedConnectorEntity? = null) : PairedConnectorLocalDataSource {
    val rows = mutableMapOf<String, PairedConnectorEntity>()

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
