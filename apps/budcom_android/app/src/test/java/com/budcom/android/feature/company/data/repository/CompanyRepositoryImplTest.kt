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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
    fun `legacy estimation ID resolves and persists complete selection`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("estimation", legacy = true)
        val remote = FakeRemote(
            selectResult = ApiResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = ApiResult.Success(
                SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200),
            ),
        )
        val repository = CompanyRepositoryImpl(remote, FakeCompanyLocal(), store, errorMapper, dispatchers)

        repository.restoreSelection()

        assertEquals(SessionSelectedCompany("estimation", "ESTIMATION"), store.currentCompany)
        assertEquals(1, store.completeSaveCount)
    }

    @Test
    fun `legacy Budcom ID persists complete selection across repository recreation`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("budcom-test-01", legacy = true)
        val remote = FakeRemote(
            selectResult = ApiResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("budcom-test-01"), null, 200)),
            validateResult = ApiResult.Success(
                SessionValidationOutcome("SUCCESS", sampleSession("budcom-test-01"), null, "budcom-test-01", "Budcom-Test-01", 200),
            ),
        )
        CompanyRepositoryImpl(remote, FakeCompanyLocal(), store, errorMapper, dispatchers).restoreSelection()
        val recreated = CompanyRepositoryImpl(remote, FakeCompanyLocal(), store, errorMapper, dispatchers)

        assertEquals(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"), recreated.observeSelectedCompany().first())
        assertEquals(1, store.completeSaveCount)
    }

    @Test
    fun `unknown legacy ID remains without inheriting a name`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("unknown-company", legacy = true)
        val repository = CompanyRepositoryImpl(
            FakeRemote(
                selectResult = ApiResult.Success(
                    CompanySelectionOutcome("COMPANY_NOT_FOUND", sampleSession(null), "not found", 404),
                ),
            ),
            FakeCompanyLocal(), store, errorMapper, dispatchers,
        )

        repository.restoreSelection()

        assertEquals("unknown-company", store.currentId)
        assertEquals(null, store.currentCompany)
        assertEquals(0, store.completeSaveCount)
    }

    @Test
    fun `slow legacy restoration cannot overwrite newer user selection`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("estimation", legacy = true)
        val validationStarted = CompletableDeferred<Unit>()
        val allowValidation = CompletableDeferred<Unit>()
        val remote = FakeRemote(
            selectResult = ApiResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = ApiResult.Success(
                SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200),
            ),
            beforeValidate = {
                validationStarted.complete(Unit)
                allowValidation.await()
            },
        )
        val repository = CompanyRepositoryImpl(remote, FakeCompanyLocal(), store, errorMapper, dispatchers)

        val restoration = launch { repository.restoreSelection() }
        runCurrent()
        validationStarted.await()
        store.saveSelectedCompany(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"))
        allowValidation.complete(Unit)
        restoration.join()

        assertEquals(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"), store.currentCompany)
    }

    @Test
    fun `slow invalid restoration cannot clear newer user selection`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("estimation")
        val validationStarted = CompletableDeferred<Unit>()
        val allowValidation = CompletableDeferred<Unit>()
        val remote = FakeRemote(
            selectResult = ApiResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = ApiResult.Success(
                SessionValidationOutcome("INVALID", sampleSession(null), "invalid", "estimation", null, 409),
            ),
            beforeValidate = {
                validationStarted.complete(Unit)
                allowValidation.await()
            },
        )
        val repository = CompanyRepositoryImpl(remote, FakeCompanyLocal(), store, errorMapper, dispatchers)

        val restoration = launch { repository.restoreSelection() }
        runCurrent()
        validationStarted.await()
        store.saveSelectedCompany(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"))
        allowValidation.complete(Unit)
        restoration.join()

        assertEquals(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"), store.currentCompany)
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
        val repository = CompanyRepositoryImpl(
            remoteDataSource = remote,
            localDataSource = FakeCompanyLocal(),
            selectedCompanyStore = localStore,
            errorMapper = errorMapper,
            dispatchers = dispatchers,
        )

        val result = repository.selectCompany("estimation")
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
        assertEquals("ESTIMATION", localStore.currentName)

        remote.selectResult = ApiResult.Success(
            CompanySelectionOutcome(
                "SUCCESS",
                sampleSession("budcom-test-01"),
                null,
                200,
            ),
        )
        remote.validateResult = ApiResult.Success(
            SessionValidationOutcome(
                "SUCCESS",
                sampleSession("budcom-test-01"),
                null,
                "budcom-test-01",
                "Budcom-Test-01",
                200,
            ),
        )

        repository.selectCompany("budcom-test-01")
        assertEquals("budcom-test-01", localStore.currentId)
        assertEquals("Budcom-Test-01", localStore.currentName)
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
    var beforeValidate: suspend () -> Unit = {},
) : CompanyRemoteDataSource {
    override suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot> = companiesResult
    override suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot> = sessionResult
    override suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome> = selectResult
    override suspend fun validateSession(): ApiResult<SessionValidationOutcome> {
        beforeValidate()
        return validateResult
    }
    override suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot> = clearResult
}

private class FakeSelectedCompanyStore(
    initial: String? = null,
    legacy: Boolean = false,
) : SelectedCompanyStore {
    private val flow = MutableStateFlow(initial)
    private val companyFlow = MutableStateFlow(
        if (legacy) null else initial?.let { SessionSelectedCompany(it, it.uppercase()) },
    )
    var currentId: String? = initial
        private set
    var currentName: String? = null
        private set
    val currentCompany: SessionSelectedCompany?
        get() = companyFlow.value
    var completeSaveCount: Int = 0
        private set

    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = companyFlow

    override fun observeSelectedCompanyId(): Flow<String?> = flow

    override suspend fun getSelectedCompanyId(): String? = currentId

    override suspend fun saveSelectedCompanyId(companyId: String) {
        currentId = companyId
        flow.value = companyId
    }

    override suspend fun saveSelectedCompany(company: SessionSelectedCompany) {
        currentId = company.id
        currentName = company.name
        flow.value = company.id
        companyFlow.value = company
        completeSaveCount++
    }

    override suspend fun saveSelectedCompanyIfCurrentId(
        expectedCompanyId: String?,
        company: SessionSelectedCompany,
    ): Boolean {
        if (currentId != expectedCompanyId) return false
        saveSelectedCompany(company)
        return true
    }

    override suspend fun clearSelectedCompanyId() {
        currentId = null
        currentName = null
        flow.value = null
        companyFlow.value = null
    }

    override suspend fun clearSelectedCompanyIfCurrentId(expectedCompanyId: String): Boolean {
        if (currentId != expectedCompanyId) return false
        clearSelectedCompanyId()
        return true
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
