package com.jajusri.venture.feature.serverconfig.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.DefaultConnectorBaseUrlProvider
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorServiceStatus
import com.jajusri.venture.feature.serverconfig.domain.repository.ConnectorConfigRepository
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var viewModel: ServerConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = ServerConfigViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restores saved URL from repository`() = runTest(dispatcher) {
        repository.baseUrl.value = "http://192.168.0.5:8080/"
        advanceUntilIdle()
        assertEquals("http://192.168.0.5:8080/", viewModel.uiState.value.savedUrl)
        assertEquals("http://192.168.0.5:8080/", viewModel.uiState.value.urlInput)
    }

    @Test
    fun `invalid URL sets inline validation error`() = runTest(dispatcher) {
        viewModel.onEvent(ServerConfigEvent.UrlChanged("ftp://bad"))
        assertTrue(viewModel.uiState.value.urlValidationError != null)
    }

    @Test
    fun `save success updates saved URL`() = runTest(dispatcher) {
        viewModel.onEvent(ServerConfigEvent.UrlChanged("http://192.168.1.20:8080"))
        viewModel.onEvent(ServerConfigEvent.SaveClicked)
        advanceUntilIdle()
        assertEquals("http://192.168.1.20:8080/", viewModel.uiState.value.savedUrl)
        assertEquals("Connector URL saved.", viewModel.uiState.value.saveFeedback)
    }

    @Test
    fun `test connection success updates connection state`() = runTest(dispatcher) {
        repository.probeResult = AppResult.Success(sampleProbe())
        viewModel.onEvent(ServerConfigEvent.TestConnectionClicked)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connection is ConnectionUiState.Success)
    }

    @Test
    fun `test connection offline maps error kind`() = runTest(dispatcher) {
        repository.probeResult = AppResult.Failure(AppError.Offline())
        viewModel.onEvent(ServerConfigEvent.TestConnectionClicked)
        advanceUntilIdle()
        val error = viewModel.uiState.value.connection as ConnectionUiState.Error
        assertEquals(ConnectionErrorKind.Offline, error.kind)
    }

    @Test
    fun `retry re-runs connection test`() = runTest(dispatcher) {
        repository.probeResult = AppResult.Failure(AppError.Timeout())
        viewModel.onEvent(ServerConfigEvent.TestConnectionClicked)
        advanceUntilIdle()
        repository.probeResult = AppResult.Success(sampleProbe())
        viewModel.onEvent(ServerConfigEvent.RetryClicked)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connection is ConnectionUiState.Success)
    }

    @Test
    fun `busy state ignores duplicate test submissions`() = runTest(dispatcher) {
        repository.holdTest = true
        viewModel.onEvent(ServerConfigEvent.TestConnectionClicked)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTesting)
        viewModel.onEvent(ServerConfigEvent.TestConnectionClicked)
        viewModel.onEvent(ServerConfigEvent.SaveClicked)
        assertEquals(1, repository.testCalls)
        repository.completeHeldTest()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isTesting)
    }
}

private class FakeRepository : ConnectorConfigRepository {
    val baseUrl = MutableStateFlow(DefaultConnectorBaseUrlProvider.DEFAULT)
    var probeResult: AppResult<ConnectorConnectionProbe> =
        AppResult.Failure(AppError.Unexpected(IllegalStateException("unset")))
    var holdTest: Boolean = false
    var testCalls: Int = 0
    private var heldContinuation: CancellableContinuation<Unit>? = null

    override fun observeBaseUrl(): Flow<String> = baseUrl

    override fun currentBaseUrl(): String = baseUrl.value

    override suspend fun saveBaseUrl(rawUrl: String): AppResult<String> {
        val normalized = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        baseUrl.value = normalized
        return AppResult.Success(normalized)
    }

    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> {
        testCalls += 1
        if (holdTest) {
            suspendCancellableCoroutine { cont ->
                heldContinuation = cont
            }
        }
        return probeResult
    }

    fun completeHeldTest() {
        holdTest = false
        heldContinuation?.resume(Unit)
        heldContinuation = null
    }
}

private fun sampleProbe() = ConnectorConnectionProbe(
    health = ConnectorHealth(
        status = "ok",
        schemaVersion = "1.0.0",
        connectorVersion = "0.4.0",
        tallyReachable = true,
        readOnly = true,
        bindHost = "127.0.0.1",
        bindPort = 8080,
        networkExposure = "loopback",
        networkExposureWarning = null,
        networkPolicySatisfied = true,
        authenticatedLanAccessEnabled = false,
        services = listOf(ConnectorServiceStatus("ApiServer", true, true)),
        startupCorrelationId = null,
        repositoryAvailable = true,
        databaseAccessible = true,
    ),
    readiness = ConnectorReadiness(
        status = "ready",
        repositoryAvailable = true,
        databaseAccessible = true,
        voucherSynchronizationComposed = true,
        voucherApplicationComposed = true,
        httpStatus = 200,
    ),
    checkedAtEpochMillis = 99L,
)
