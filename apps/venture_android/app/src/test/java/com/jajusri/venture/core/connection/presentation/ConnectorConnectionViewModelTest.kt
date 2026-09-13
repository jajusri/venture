package com.jajusri.venture.core.connection.presentation

import app.cash.turbine.test
import com.jajusri.venture.core.connection.ConnectionResolution
import com.jajusri.venture.core.connection.ConnectorConnectionResolver
import com.jajusri.venture.core.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorConnectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is resolving before any resolution runs`() = runTest(dispatcher) {
        val vm = ConnectorConnectionViewModel(FakeResolver(ConnectionResolution.Offline), TimeProvider { 1L })

        vm.uiState.test {
            assertEquals(ConnectorConnectionUiState.Resolving, awaitItem())
        }
    }

    @Test
    fun `successful resolution emits reconnecting then connected`() = runTest(dispatcher) {
        val resolution = ConnectionResolution.Connected(
            connectorId = "cid-1",
            friendlyName = "Front Desk",
            host = "192.168.1.20",
            port = 8080,
        )
        val vm = ConnectorConnectionViewModel(FakeResolver(resolution), TimeProvider { 500L })

        vm.uiState.test {
            assertEquals(ConnectorConnectionUiState.Resolving, awaitItem())
            vm.resolveConnection()
            assertEquals(ConnectorConnectionUiState.Reconnecting, awaitItem())
            val connected = awaitItem() as ConnectorConnectionUiState.Connected
            assertEquals("cid-1", connected.connectorId)
            assertEquals("Front Desk", connected.pairedName)
            assertEquals(500L, connected.lastSuccessfulConnectionAtEpochMillis)
        }
    }

    @Test
    fun `failed resolution emits reconnecting then offline`() = runTest(dispatcher) {
        val vm = ConnectorConnectionViewModel(FakeResolver(ConnectionResolution.Offline), TimeProvider { 1L })

        vm.uiState.test {
            awaitItem()
            vm.resolveConnection()
            assertEquals(ConnectorConnectionUiState.Reconnecting, awaitItem())
            assertEquals(ConnectorConnectionUiState.Offline, awaitItem())
        }
    }
}

private class FakeResolver(private val result: ConnectionResolution) : ConnectorConnectionResolver {
    override suspend fun resolve(): ConnectionResolution = result
}
