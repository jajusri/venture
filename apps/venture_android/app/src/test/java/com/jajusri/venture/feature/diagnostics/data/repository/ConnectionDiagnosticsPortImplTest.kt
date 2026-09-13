package com.jajusri.venture.feature.diagnostics.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.DefaultErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.RetryPolicy
import com.jajusri.venture.feature.diagnostics.data.remote.ConnectionDiagnosticsDto
import com.jajusri.venture.feature.diagnostics.data.remote.ConnectionDiagnosticsEnvelopeDto
import com.jajusri.venture.feature.diagnostics.data.remote.DiagnosticsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConnectionDiagnosticsPortImplTest {
    private val connectivity = object : NetworkConnectivityObserver {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
        override fun current(): Boolean = true
    }
    private val errorMapper = DefaultErrorMapper(Json { ignoreUnknownKeys = true }, connectivity)

    @Test
    fun mapsSuccessfulConnectionDiagnostics() = runTest {
        val api = FakeDiagnosticsApi(
            ConnectionDiagnosticsEnvelopeDto(
                schemaVersion = "1.0.0",
                connection = ConnectionDiagnosticsDto(
                    state = "connected",
                    host = "127.0.0.1",
                    port = 9000,
                    circuitState = "closed",
                    totalRequests = 3,
                ),
            ),
        )
        val port = ConnectionDiagnosticsPortImpl(
            api = api,
            errorMapper = errorMapper,
            connectivityObserver = connectivity,
            retryPolicy = RetryPolicy.None,
        )
        val result = port.loadConnectionDiagnostics() as AppResult.Success
        assertEquals("connected", result.value.state)
        assertEquals(3, result.value.totalRequests)
        assertEquals(1, api.calls)
    }

    @Test
    fun mapsTransportFailure() = runTest {
        val api = FakeDiagnosticsApi(error = IOException("boom"))
        val port = ConnectionDiagnosticsPortImpl(
            api = api,
            errorMapper = errorMapper,
            connectivityObserver = connectivity,
            retryPolicy = RetryPolicy.None,
        )
        val result = port.loadConnectionDiagnostics()
        assertTrue(result is AppResult.Failure)
    }
}

private class FakeDiagnosticsApi(
    private val response: ConnectionDiagnosticsEnvelopeDto? = null,
    private val error: Throwable? = null,
) : DiagnosticsApi {
    var calls = 0
    override suspend fun getConnectionDiagnostics(): ConnectionDiagnosticsEnvelopeDto {
        calls += 1
        error?.let { throw it }
        return response ?: error("missing response")
    }
}
