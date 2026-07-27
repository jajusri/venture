package com.budcom.android.feature.serverconfig.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialization fixtures mirror Connector integration expectations
 * (server.test.ts / voucher-hardening.test.ts).
 */
class ConnectorSystemDtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    @Test
    fun `decodes health envelope from connector fixture`() {
        val raw = """
            {
              "status": "unavailable",
              "schemaVersion": "1.0.0",
              "connectorVersion": "0.4.0",
              "tallyReachable": false,
              "readOnly": true,
              "bindHost": "127.0.0.1",
              "bindPort": 8080,
              "networkExposure": "loopback",
              "networkExposureWarning": null,
              "networkPolicySatisfied": true,
              "authenticatedLanAccessEnabled": false,
              "services": [
                {"name": "ApiServer", "running": false, "ready": false}
              ],
              "startupCorrelationId": null,
              "repositoryAvailable": false,
              "databaseAccessible": false
            }
        """.trimIndent()

        val dto = json.decodeFromString(HealthResponseDto.serializer(), raw)
        val domain = dto.toDomain()
        assertEquals("unavailable", domain.status)
        assertEquals("1.0.0", domain.schemaVersion)
        assertEquals("0.4.0", domain.connectorVersion)
        assertEquals(false, domain.tallyReachable)
        assertTrue(domain.readOnly)
        assertEquals("127.0.0.1", domain.bindHost)
        assertEquals(8080, domain.bindPort)
        assertEquals("loopback", domain.networkExposure)
        assertNull(domain.networkExposureWarning)
        assertTrue(domain.networkPolicySatisfied)
        assertEquals(false, domain.authenticatedLanAccessEnabled)
        assertEquals(1, domain.services.size)
        assertEquals("ApiServer", domain.services[0].name)
        assertNull(domain.startupCorrelationId)
        assertEquals(false, domain.repositoryAvailable)
        assertEquals(false, domain.databaseAccessible)
    }

    @Test
    fun `decodes readiness ready and not_ready fixtures`() {
        val readyRaw = """
            {
              "status": "ready",
              "repositoryAvailable": true,
              "databaseAccessible": true,
              "voucherSynchronizationComposed": true,
              "voucherApplicationComposed": true
            }
        """.trimIndent()
        val ready = json.decodeFromString(ReadinessResponseDto.serializer(), readyRaw).toDomain(200)
        assertEquals("ready", ready.status)
        assertEquals(200, ready.httpStatus)

        val notReadyRaw = """
            {
              "status": "not_ready",
              "repositoryAvailable": false,
              "databaseAccessible": false,
              "voucherSynchronizationComposed": false,
              "voucherApplicationComposed": false
            }
        """.trimIndent()
        val notReady = json.decodeFromString(ReadinessResponseDto.serializer(), notReadyRaw)
            .toDomain(503)
        assertEquals("not_ready", notReady.status)
        assertEquals(503, notReady.httpStatus)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `rejects malformed health json`() {
        json.decodeFromString(HealthResponseDto.serializer(), """{"status":"ok"}""")
    }
}
