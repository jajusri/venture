package com.budcom.android.core.connection

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorHealthProbeTest {
    private val server = MockWebServer()
    private val probe = OkHttpConnectorHealthProbe()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reachable connector returns connectorId and connectorName`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"ok","connectorId":"cid-123","connectorName":"Front Desk"}""",
            ),
        )
        server.start()

        val result = probe.probe(server.hostName, server.port, 2_000L)

        assertTrue(result.reachable)
        assertEquals("cid-123", result.connectorId)
        assertEquals("Front Desk", result.connectorName)
    }

    @Test
    fun `non-2xx response is treated as unreachable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()

        val result = probe.probe(server.hostName, server.port, 2_000L)

        assertFalse(result.reachable)
        assertNull(result.connectorId)
    }

    @Test
    fun `malformed body is treated as unreachable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        server.start()

        val result = probe.probe(server.hostName, server.port, 2_000L)

        assertFalse(result.reachable)
        assertNull(result.connectorId)
    }

    @Test
    fun `connection refused returns unreachable without throwing`() = runTest {
        val result = probe.probe("127.0.0.1", 1, 500L)

        assertFalse(result.reachable)
        assertNull(result.connectorId)
        assertNull(result.connectorName)
    }
}
