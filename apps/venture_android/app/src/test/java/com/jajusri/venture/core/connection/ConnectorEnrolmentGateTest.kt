package com.jajusri.venture.core.connection

import com.jajusri.venture.core.connection.data.local.PairedConnectorEntity
import com.jajusri.venture.core.connection.data.local.PairedConnectorLocalDataSource
import com.jajusri.venture.core.network.DefaultConnectorBaseUrlProvider
import com.jajusri.venture.core.util.DeviceEnvironment
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorEnrolmentGateTest {

    @Test
    fun `unpaired physical device with untouched default url needs enrolment`() = runTest {
        val gate = gateOf(paired = null, isEmulator = false, persistedUrl = DefaultConnectorBaseUrlProvider.DEFAULT)

        assertTrue(gate.needsEnrolment())
    }

    @Test
    fun `already paired device never needs enrolment, regardless of device type or url`() = runTest {
        // Covers requirement 11 (future launches use the stored Connector): once paired,
        // the gate must never redirect back into first-install discovery.
        val paired = PairedConnectorEntity(
            connectorId = "cid-1",
            friendlyName = "Front Desk",
            lastKnownHost = "192.168.1.20",
            lastKnownPort = 8080,
            lastConnectedAtEpochMillis = 1L,
            createdAtEpochMillis = 1L,
        )
        val gate = gateOf(paired = paired, isEmulator = false, persistedUrl = DefaultConnectorBaseUrlProvider.DEFAULT)

        assertFalse(gate.needsEnrolment())
    }

    @Test
    fun `emulator development behaviour remains supported, even when unpaired`() = runTest {
        val gate = gateOf(paired = null, isEmulator = true, persistedUrl = DefaultConnectorBaseUrlProvider.DEFAULT)

        assertFalse(gate.needsEnrolment())
    }

    @Test
    fun `an explicitly configured non-default url is treated as a developer configuration, not redirected`() = runTest {
        val gate = gateOf(paired = null, isEmulator = false, persistedUrl = "http://192.168.50.10:8080/")

        assertFalse(gate.needsEnrolment())
    }

    private fun gateOf(
        paired: PairedConnectorEntity?,
        isEmulator: Boolean,
        persistedUrl: String,
    ): ConnectorEnrolmentGate = DefaultConnectorEnrolmentGate(
        pairedConnectors = GateFakePairedConnectorLocalDataSource(paired),
        deviceEnvironment = object : DeviceEnvironment {
            override fun isLikelyEmulator() = isEmulator
        },
        baseUrlLocalStore = object : ConnectorBaseUrlLocalStore {
            override val baseUrl: Flow<String> = flowOf(persistedUrl)
            override suspend fun save(normalizedBaseUrl: String) = Unit
            override suspend fun read(): String = persistedUrl
        },
    )
}

private class GateFakePairedConnectorLocalDataSource(
    initial: PairedConnectorEntity?,
) : PairedConnectorLocalDataSource {
    private val rows = mutableMapOf<String, PairedConnectorEntity>()

    init {
        initial?.let { rows[it.connectorId] = it }
    }

    override suspend fun findByConnectorId(connectorId: String): PairedConnectorEntity? = rows[connectorId]
    override suspend fun getPrimary(): PairedConnectorEntity? = rows.values.maxByOrNull { it.lastConnectedAtEpochMillis }
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
        rows[connectorId] = existing.copy(lastKnownHost = host, lastKnownPort = port, lastConnectedAtEpochMillis = connectedAtEpochMillis)
    }
}
