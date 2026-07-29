package com.budcom.android.feature.company.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.local.CompanyLocalDataSource
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.CompanySelectionOutcome
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = NetworkError.Unknown()
        override fun toAppError(error: NetworkError): AppError = when (error) {
            is NetworkError.NoConnectivity -> AppError.Offline()
            is NetworkError.Timeout -> AppError.Timeout()
            is NetworkError.Http -> AppError.Remote(error.httpStatus, error.code, error.message)
            is NetworkError.Serialization -> AppError.Serialization(error.message)
            is NetworkError.Unknown -> AppError.Unexpected(IllegalStateException(error.message))
        }
    }

    @Test
    fun `restore selection persists selected company after successful validate`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "estimation")
        val remote = FakeRemote(
            selectResult = ApiResult.Success(
                CompanySelectionOutcome(
                    status = "SUCCESS",
                    session = sampleSession(selectedId = "estimation"),
                    reason = null,
                    httpStatus = 200,
                ),
            ),
            validateResult = ApiResult.Success(
                SessionValidationOutcome(
                    status = "SUCCESS",
                    session = sampleSession(selectedId = "estimation"),
                    reason = null,
                    companyId = "estimation",
                    companyName = "ESTIMATION",
                    httpStatus = 200,
                ),
            ),
        )
        val repository = CompanyRepositoryImpl(remote, FakeCompanyLocal(), localStore, errorMapper, dispatchers)

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
    }

    @Test
    fun `restore selection hydrates local cache from connector session when local empty`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = null)
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(
                sessionResult = ApiResult.Success(sampleSession(selectedId = "budcom-test-01")),
                validateResult = ApiResult.Success(
                    SessionValidationOutcome(
                        status = "SUCCESS",
                        session = sampleSession(selectedId = "budcom-test-01"),
                        reason = null,
                        companyId = "budcom-test-01",
                        companyName = "Budcom-Test-01",
                        httpStatus = 200,
                    ),
                ),
            ),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals("budcom-test-01", localStore.currentId)
        assertEquals("Budcom-Test-01", (result as AppResult.Success).value?.companyName)
    }

    @Test
    fun `restore selection leaves local empty when connector session has no company`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = null)
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(
                sessionResult = ApiResult.Success(sampleSession(selectedId = null)),
            ),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals(null, (result as AppResult.Success).value)
        assertEquals(null, localStore.currentId)
    }

    @Test
    fun `restore selection clears persisted ID on invalid selection status`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "ghost")
        val remote = FakeRemote(
            selectResult = ApiResult.Success(
                CompanySelectionOutcome(
                    status = "COMPANY_NOT_FOUND",
                    session = sampleSession(selectedId = null),
                    reason = "not found",
                    httpStatus = 404,
                ),
            ),
        )
        val repository = CompanyRepositoryImpl(remote, FakeCompanyLocal(), localStore, errorMapper, dispatchers)

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Failure)
        assertEquals(null, localStore.currentId)
    }

    @Test
    fun `restore selection keeps cached ID when connector is offline`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "budcom-test-01")
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(selectResult = ApiResult.Failure(NetworkError.NoConnectivity)),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Offline)
        assertEquals("budcom-test-01", localStore.currentId)
    }

    @Test
    fun `restore selection keeps cached ID when session validate times out`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "budcom-test-01")
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(
                selectResult = ApiResult.Success(
                    CompanySelectionOutcome(
                        status = "DUPLICATE_SELECTION",
                        session = sampleSession(selectedId = "budcom-test-01"),
                        reason = null,
                        httpStatus = 200,
                    ),
                ),
                validateResult = ApiResult.Failure(NetworkError.Timeout()),
            ),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Timeout)
        assertEquals("budcom-test-01", localStore.currentId)
    }

    @Test
    fun `select company maps offline failure`() = runTest(dispatcher) {
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(selectResult = ApiResult.Failure(NetworkError.NoConnectivity)),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = FakeSelectedCompanyStore(),
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.selectCompany("estimation")
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Offline)
    }

    @Test
    fun `select company validates session and stores selected ID`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore()
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(
                selectResult = ApiResult.Success(
                    CompanySelectionOutcome(
                        status = "SUCCESS",
                        session = sampleSession(selectedId = "estimation"),
                        reason = null,
                        httpStatus = 200,
                    ),
                ),
                validateResult = ApiResult.Success(
                    SessionValidationOutcome(
                        status = "SUCCESS",
                        session = sampleSession(selectedId = "estimation"),
                        reason = null,
                        companyId = "estimation",
                        companyName = "ESTIMATION",
                        httpStatus = 200,
                    ),
                ),
            ),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.selectCompany("estimation")
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
    }

    @Test
    fun `load companies writes cache on success`() = runTest(dispatcher) {
        val local = FakeCompanyLocal()
        val snapshot = sampleDiscovery()
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(companiesResult = ApiResult.Success(snapshot)),
            localDataSource = local,
            selectedCompanyStore = FakeSelectedCompanyStore(),
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )
        val result = repository.loadCompanies() as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals(snapshot, local.cached)
    }

    @Test
    fun `load companies returns cache when offline`() = runTest(dispatcher) {
        val cached = sampleDiscovery()
        val local = FakeCompanyLocal(cached)
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(companiesResult = ApiResult.Failure(NetworkError.NoConnectivity)),
            localDataSource = local,
            selectedCompanyStore = FakeSelectedCompanyStore(),
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )
        val result = repository.loadCompanies() as AppResult.Success
        assertEquals("budcom-test-01", result.value.items.single().id)
    }

    @Test
    fun `load companies offline without cache fails`() = runTest(dispatcher) {
        val repository = CompanyRepositoryImpl(
            remoteDataSource = FakeRemote(companiesResult = ApiResult.Failure(NetworkError.NoConnectivity)),
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = FakeSelectedCompanyStore(),
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )
        val result = repository.loadCompanies() as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }
}

private class FakeRemote(
    var companiesResult: ApiResult<CompanyDiscoverySnapshot> = ApiResult.Success(
        CompanyDiscoverySnapshot(
            items = emptyList(),
            schemaVersion = "1.0.0",
            dataFreshnessAt = "2026-01-01T00:00:00Z",
            contractVersion = "1",
            status = "SUCCESS",
            tallyReachable = true,
            dataQualityStatus = null,
            dataQualityReason = null,
            reason = null,
        ),
    ),
    var sessionResult: ApiResult<ConnectorSessionSnapshot> = ApiResult.Success(sampleSession(null)),
    var selectResult: ApiResult<CompanySelectionOutcome> = ApiResult.Success(
        CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200),
    ),
    var validateResult: ApiResult<SessionValidationOutcome> = ApiResult.Success(
        SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200),
    ),
    var clearResult: ApiResult<ConnectorSessionSnapshot> = ApiResult.Success(sampleSession(null)),
) : CompanyRemoteDataSource {
    override suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot> = companiesResult
    override suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot> = sessionResult
    override suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome> = selectResult
    override suspend fun validateSession(): ApiResult<SessionValidationOutcome> = validateResult
    override suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot> = clearResult
}

private class FakeSelectedCompanyStore(
    initial: String? = null,
) : SelectedCompanyStore {
    private val flow = MutableStateFlow(initial)
    var currentId: String? = initial
        private set

    override fun observeSelectedCompanyId(): Flow<String?> = flow

    override suspend fun getSelectedCompanyId(): String? = currentId

    override suspend fun saveSelectedCompanyId(companyId: String) {
        currentId = companyId
        flow.value = companyId
    }

    override suspend fun clearSelectedCompanyId() {
        currentId = null
        flow.value = null
    }
}

private class FakeCompanyLocal(
    initial: CompanyDiscoverySnapshot? = null,
) : CompanyLocalDataSource {
    var cached: CompanyDiscoverySnapshot? = initial

    override suspend fun hasCache(): Boolean = cached != null

    override suspend fun readSnapshot(): CompanyDiscoverySnapshot? = cached

    override suspend fun replaceSnapshot(snapshot: CompanyDiscoverySnapshot) {
        cached = snapshot
    }
}

private fun sampleSession(selectedId: String?): ConnectorSessionSnapshot = ConnectorSessionSnapshot(
    sessionId = "s1",
    selectedCompany = selectedId?.let { SessionSelectedCompany(id = it, name = it.uppercase()) },
    connectionStatus = "connected",
    connectorVersion = "0.4.0",
    erpType = "tally",
    selectedAt = null,
    lastValidatedAt = null,
    createdAt = "2026-01-01T00:00:00Z",
    contractVersion = "1",
)

private fun sampleDiscovery() = CompanyDiscoverySnapshot(
    items = listOf(
        ConnectorCompany(
            id = "budcom-test-01",
            name = "Budcom-Test-01",
            financialYear = null,
            booksFrom = null,
            baseCurrency = "INR",
        ),
    ),
    schemaVersion = "1.0.0",
    dataFreshnessAt = "2026-01-01T00:00:00Z",
    contractVersion = "1",
    status = "SUCCESS",
    tallyReachable = true,
    dataQualityStatus = null,
    dataQualityReason = null,
    reason = null,
)
