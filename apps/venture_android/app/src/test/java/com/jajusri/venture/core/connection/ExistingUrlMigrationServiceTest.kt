package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.network.ConnectorBaseUrlProvider
import com.jajusri.venture.core.util.TimeProvider
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

class ExistingUrlMigrationServiceTest {

    @Test
    fun `unreachable connector leaves paired storage untouched`() = runTest {
        val local = FakeLocal()
        val service = DefaultExistingUrlMigrationService(
            baseUrlProvider = MigrationBaseUrlProviderFake("http://10.0.2.2:8080/"),
            healthProbe = MigrationHealthProbeFake(ConnectorHealthProbeResult(false, null, null)),
            pairedConnectors = local,
            timeProvider = TimeProvider { 100L },
        )

        val outcome = service.migrateIfNeeded()

        assertEquals(MigrationOutcome.Unreachable, outcome)
        assertTrue(local.rows.isEmpty())
    }

    @Test
    fun `first successful probe inserts a new paired record`() = runTest {
        val local = FakeLocal()
        val service = DefaultExistingUrlMigrationService(
            baseUrlProvider = MigrationBaseUrlProviderFake("http://10.0.2.2:8080/"),
            healthProbe = MigrationHealthProbeFake(ConnectorHealthProbeResult(true, "cid-1", "Front Desk")),
            pairedConnectors = local,
            timeProvider = TimeProvider { 100L },
        )

        val outcome = service.migrateIfNeeded() as MigrationOutcome.Migrated

        assertEquals("cid-1", outcome.connectorId)
        val row = local.rows.getValue("cid-1")
        assertEquals("Front Desk", row.friendlyName)
        assertEquals("10.0.2.2", row.lastKnownHost)
        assertEquals(8080, row.lastKnownPort)
    }

    @Test
    fun `repeated migration for the same connector is idempotent`() = runTest {
        val local = FakeLocal()
        val service = DefaultExistingUrlMigrationService(
            baseUrlProvider = MigrationBaseUrlProviderFake("http://10.0.2.2:8080/"),
            healthProbe = MigrationHealthProbeFake(ConnectorHealthProbeResult(true, "cid-1", "Front Desk")),
            pairedConnectors = local,
            timeProvider = TimeProvider { 100L },
        )

        service.migrateIfNeeded()
        val second = service.migrateIfNeeded()

        assertEquals(MigrationOutcome.AlreadyPaired("cid-1"), second)
        assertEquals(1, local.rows.size)
    }

    @Test
    fun `missing connector name falls back to a default friendly name`() = runTest {
        val local = FakeLocal()
        val service = DefaultExistingUrlMigrationService(
            baseUrlProvider = MigrationBaseUrlProviderFake("http://10.0.2.2:8080/"),
            healthProbe = MigrationHealthProbeFake(ConnectorHealthProbeResult(true, "cid-1", null)),
            pairedConnectors = local,
            timeProvider = TimeProvider { 100L },
        )

        service.migrateIfNeeded()

        assertEquals("Venture Connector", local.rows.getValue("cid-1").friendlyName)
    }

    @Test
    fun `reachable response without a connectorId is treated as unreachable`() = runTest {
        val local = FakeLocal()
        val service = DefaultExistingUrlMigrationService(
            baseUrlProvider = MigrationBaseUrlProviderFake("http://10.0.2.2:8080/"),
            healthProbe = MigrationHealthProbeFake(ConnectorHealthProbeResult(true, null, null)),
            pairedConnectors = local,
            timeProvider = TimeProvider { 100L },
        )

        val outcome = service.migrateIfNeeded()

        assertEquals(MigrationOutcome.Unreachable, outcome)
        assertNull(local.rows["cid-1"])
    }
}

private class MigrationHealthProbeFake(private val result: ConnectorHealthProbeResult) : ConnectorHealthProbe {
    override suspend fun probe(host: String, port: Int, timeoutMs: Long): ConnectorHealthProbeResult = result
}

private class FakeLocal : PairedConnectorLocalDataSource {
    val rows = mutableMapOf<String, PairedConnectorEntity>()

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

private class MigrationBaseUrlProviderFake(initial: String) : ConnectorBaseUrlProvider {
    private val flow = MutableStateFlow(initial)
    override fun snapshot(): String = flow.value
    override fun snapshotHttpUrl(): HttpUrl = flow.value.toHttpUrl()
    override fun observe(): Flow<String> = flow.asStateFlow()
    override fun updateInMemory(normalizedBaseUrl: String) {
        flow.value = normalizedBaseUrl
    }
}
