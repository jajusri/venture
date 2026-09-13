package com.jajusri.venture.feature.diagnostics.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionDiagnosticsDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserializesConfirmedConnectionEnvelope() {
        val dto = json.decodeFromString(
            ConnectionDiagnosticsEnvelopeDto.serializer(),
            """
            {
              "schemaVersion":"1.0.0",
              "connection":{
                "state":"connected",
                "host":"127.0.0.1",
                "port":9000,
                "lastSuccessfulPingAt":"2026-07-27T10:00:00.000Z",
                "lastErrorAt":null,
                "lastErrorCode":null,
                "lastErrorMessage":null,
                "totalRequests":12,
                "failedRequests":1,
                "reconnectAttempts":0,
                "averageLatencyMs":42.5,
                "poolActiveConnections":1,
                "poolWaitingRequests":0,
                "safeMode":false,
                "circuitState":"closed",
                "lastRequest":{
                  "correlationId":"c1",
                  "sentAt":"2026-07-27T10:00:00.000Z",
                  "outcome":"success"
                },
                "runtimeLimits":{
                  "timeoutMs":5000
                }
              }
            }
            """.trimIndent(),
        )
        val domain = dto.connection.toDomain()
        assertEquals("connected", domain.state)
        assertEquals("127.0.0.1", domain.host)
        assertEquals(9000, domain.port)
        assertEquals("c1", domain.lastRequestCorrelationId)
        assertEquals("success", domain.lastRequestOutcome)
        assertEquals(5000, domain.runtimeTimeoutMs)
        assertEquals(42.5, domain.averageLatencyMs, 0.001)
    }

    @Test
    fun mapsMissingOptionalFieldsToNull() {
        val dto = json.decodeFromString(
            ConnectionDiagnosticsDto.serializer(),
            """
            {
              "state":"disconnected",
              "host":"localhost",
              "port":9000,
              "circuitState":"open"
            }
            """.trimIndent(),
        )
        val domain = dto.toDomain()
        assertNull(domain.lastSuccessfulPingAt)
        assertNull(domain.lastRequestCorrelationId)
        assertNull(domain.runtimeTimeoutMs)
        assertEquals(0, domain.totalRequests)
    }
}
