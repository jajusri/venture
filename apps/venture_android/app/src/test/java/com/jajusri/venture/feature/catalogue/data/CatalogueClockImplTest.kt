package com.jajusri.venture.feature.catalogue.data

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestampSource
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for a real defect found live on a real device (2026-08-24): the underlying
 * `ConnectorStatusPort.probeConnection()` call can take tens of seconds to fail (this app's shared
 * retry policy) when the paired Connector is unreachable — an ordinary, common state, not an edge
 * case — and every Catalogue write calls [com.jajusri.venture.feature.catalogue.domain.port.CatalogueClock.now()].
 * These tests prove [CatalogueClockImpl] never waits on a slow probe.
 */
class CatalogueClockImplTest {

    private fun health(serverTime: Long?) = ConnectorHealth(
        status = "ok", schemaVersion = "1", connectorVersion = "1", tallyReachable = true, readOnly = true,
        bindHost = "0.0.0.0", bindPort = 8080, networkExposure = "lan", networkExposureWarning = null,
        networkPolicySatisfied = true, authenticatedLanAccessEnabled = false, services = emptyList(),
        startupCorrelationId = null, repositoryAvailable = true, databaseAccessible = true,
        serverTimeEpochMillis = serverTime,
    )

    private class SlowConnectorStatusPort(private val delayMillis: Long, private val result: AppResult<ConnectorConnectionProbe>) :
        ConnectorStatusPort {
        override fun observeBaseUrl(): Flow<String> = flowOf("http://10.0.2.2:8080")
        override fun currentBaseUrl(): String = "http://10.0.2.2:8080"
        override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> {
            delay(delayMillis)
            return result
        }
    }

    @Test
    fun `a fast, successful probe yields a Connector-sourced timestamp`() = runTest {
        val port = SlowConnectorStatusPort(10L, AppResult.Success(ConnectorConnectionProbe(health(5_000L), null, 1L)))
        val clock = CatalogueClockImpl(port, TimeProvider { 1_000L })

        val timestamp = clock.now()

        assertEquals(5_000L, timestamp.epochMillis)
        assertEquals(CatalogueTimestampSource.Connector, timestamp.source)
    }

    @Test
    fun `a probe that never resolves within the bounded wait falls back to the device clock, never hangs`() = runTest {
        val port = SlowConnectorStatusPort(60_000L, AppResult.Success(ConnectorConnectionProbe(health(5_000L), null, 1L)))
        val clock = CatalogueClockImpl(port, TimeProvider { 1_234L })

        val timestamp = clock.now()

        assertEquals(1_234L, timestamp.epochMillis)
        assertEquals(CatalogueTimestampSource.DeviceLocalProvisional, timestamp.source)
    }

    @Test
    fun `a probe failure falls back to the device clock immediately`() = runTest {
        val port = SlowConnectorStatusPort(10L, AppResult.Failure(com.jajusri.venture.core.common.AppError.Offline()))
        val clock = CatalogueClockImpl(port, TimeProvider { 9_999L })

        val timestamp = clock.now()

        assertEquals(9_999L, timestamp.epochMillis)
        assertEquals(CatalogueTimestampSource.DeviceLocalProvisional, timestamp.source)
    }

    @Test
    fun `a health payload missing serverTimeEpochMillis (older Connector build) falls back to the device clock`() = runTest {
        val port = SlowConnectorStatusPort(10L, AppResult.Success(ConnectorConnectionProbe(health(null), null, 1L)))
        val clock = CatalogueClockImpl(port, TimeProvider { 42L })

        val timestamp = clock.now()

        assertEquals(42L, timestamp.epochMillis)
        assertEquals(CatalogueTimestampSource.DeviceLocalProvisional, timestamp.source)
    }
}
