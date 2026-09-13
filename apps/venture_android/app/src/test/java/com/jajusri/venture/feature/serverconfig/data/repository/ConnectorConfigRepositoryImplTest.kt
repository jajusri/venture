package com.jajusri.venture.feature.serverconfig.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.DefaultConnectorBaseUrlProvider
import com.jajusri.venture.core.network.DefaultErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.NetworkError
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.jajusri.venture.feature.serverconfig.data.remote.ConnectorHealthRemoteDataSource
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorServiceStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorConfigRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val connectivity = FakeConnectivity(online = true)
    private val errorMapper = DefaultErrorMapper(json, connectivity)
    private val baseUrlProvider = DefaultConnectorBaseUrlProvider()

    @Test
    fun `saveBaseUrl validates persists and updates provider`() = runTest(dispatcher) {
        val local = FakeLocalStore()
        val repository = repository(local, FakeRemote())

        val result = repository.saveBaseUrl("http://192.168.1.10:8080")
        assertEquals(AppResult.Success("http://192.168.1.10:8080/"), result)
        assertEquals("http://192.168.1.10:8080/", local.saved)
        assertEquals("http://192.168.1.10:8080/", baseUrlProvider.snapshot())
    }

    @Test
    fun `saveBaseUrl does not fall back to default on invalid input`() = runTest(dispatcher) {
        baseUrlProvider.updateInMemory("http://192.168.1.10:8080/")
        val local = FakeLocalStore(initial = "http://192.168.1.10:8080/")
        val repository = repository(local, FakeRemote())

        val result = repository.saveBaseUrl("http://")
        assertTrue(result is AppResult.Failure)
        assertEquals("http://192.168.1.10:8080/", baseUrlProvider.snapshot())
        assertEquals("http://192.168.1.10:8080/", local.saved)
    }

    @Test
    fun `testConnection returns health and readiness on success`() = runTest(dispatcher) {
        val remote = FakeRemote(
            health = ApiResult.Success(sampleHealth()),
            readiness = ApiResult.Success(sampleReadiness()),
        )
        val repository = repository(FakeLocalStore(), remote)
        val result = repository.testConnection() as AppResult.Success
        assertEquals("ok", result.value.health.status)
        assertEquals("ready", result.value.readiness?.status)
        assertEquals(42L, result.value.checkedAtEpochMillis)
    }

    @Test
    fun `testConnection maps offline failure`() = runTest(dispatcher) {
        val remote = FakeRemote(health = ApiResult.Failure(NetworkError.NoConnectivity))
        val repository = repository(FakeLocalStore(), remote)
        val result = repository.testConnection() as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    @Test
    fun `testConnection maps timeout failure`() = runTest(dispatcher) {
        val remote = FakeRemote(health = ApiResult.Failure(NetworkError.Timeout()))
        val repository = repository(FakeLocalStore(), remote)
        val result = repository.testConnection() as AppResult.Failure
        assertTrue(result.error is AppError.Timeout)
    }

    @Test
    fun `testConnection maps http failure`() = runTest(dispatcher) {
        val remote = FakeRemote(
            health = ApiResult.Failure(NetworkError.Http(503, "SERVICE_UNAVAILABLE", "down")),
        )
        val repository = repository(FakeLocalStore(), remote)
        val result = repository.testConnection() as AppResult.Failure
        assertEquals(503, (result.error as AppError.Remote).httpStatus)
    }

    @Test
    fun `testConnection keeps health when readiness fails`() = runTest(dispatcher) {
        val remote = FakeRemote(
            health = ApiResult.Success(sampleHealth()),
            readiness = ApiResult.Failure(NetworkError.Timeout()),
        )
        val repository = repository(FakeLocalStore(), remote)
        val result = repository.testConnection() as AppResult.Success
        assertEquals(null, result.value.readiness)
    }

    private fun repository(
        local: ConnectorBaseUrlLocalStore,
        remote: ConnectorHealthRemoteDataSource,
    ) = ConnectorConfigRepositoryImpl(
        localDataSource = local,
        remoteDataSource = remote,
        baseUrlProvider = baseUrlProvider,
        errorMapper = errorMapper,
        dispatchers = dispatchers,
        timeProvider = TimeProvider { 42L },
    )
}

private class FakeLocalStore(
    initial: String = DefaultConnectorBaseUrlProvider.DEFAULT,
) : ConnectorBaseUrlLocalStore {
    private val flow = MutableStateFlow(initial)
    var saved: String = initial

    override val baseUrl: Flow<String> = flow

    override suspend fun save(normalizedBaseUrl: String) {
        saved = normalizedBaseUrl
        flow.value = normalizedBaseUrl
    }

    override suspend fun read(): String = saved
}

private class FakeRemote(
    private val health: ApiResult<ConnectorHealth> = ApiResult.Failure(NetworkError.Unknown()),
    private val readiness: ApiResult<ConnectorReadiness> =
        ApiResult.Failure(NetworkError.Unknown()),
) : ConnectorHealthRemoteDataSource {
    override suspend fun fetchHealth(): ApiResult<ConnectorHealth> = health
    override suspend fun fetchReadiness(): ApiResult<ConnectorReadiness> = readiness
}

private class FakeConnectivity(
    private val online: Boolean,
) : NetworkConnectivityObserver {
    override val isOnline: Flow<Boolean> = flowOf(online)
    override fun current(): Boolean = online
}

private fun sampleHealth() = ConnectorHealth(
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
    services = listOf(
        ConnectorServiceStatus("ApiServer", running = true, ready = true),
    ),
    startupCorrelationId = null,
    repositoryAvailable = true,
    databaseAccessible = true,
)

private fun sampleReadiness() = ConnectorReadiness(
    status = "ready",
    repositoryAvailable = true,
    databaseAccessible = true,
    voucherSynchronizationComposed = true,
    voucherApplicationComposed = true,
    httpStatus = 200,
)
