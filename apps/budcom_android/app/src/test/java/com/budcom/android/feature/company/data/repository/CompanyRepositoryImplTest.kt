package com.budcom.android.feature.company.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.local.CompanyLocalDataSource
import com.budcom.android.feature.company.data.remote.AuthenticatedCompanyRemoteDataSource
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.CompanySelectionOutcome
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    /**
     * Test-only construction helper. The production constructor takes no defaults for
     * [transportGate]/[authenticatedRemote] (Phase 3S-A closure correction) — every call site must
     * decide explicitly which transport a test exercises. Defaulting here to LEGACY with an
     * [UnreachableAuthenticatedRemote] means every pre-existing LEGACY-path test also proves, for
     * free, that it never reaches the authenticated transport.
     */
    private fun repository(
        remote: CompanyRemoteDataSource = FakeRemote(),
        local: CompanyLocalDataSource = FakeCompanyLocal(),
        store: SelectedCompanyStore = FakeSelectedCompanyStore(),
        transportGate: ConnectorTransportSelectionGate = FakeTransportGate(ConnectorTransportSelection.LEGACY),
        authenticatedRemote: AuthenticatedCompanyRemoteDataSource = UnreachableAuthenticatedRemote,
    ): CompanyRepositoryImpl = CompanyRepositoryImpl(remote, local, store, errorMapper, dispatchers, transportGate, authenticatedRemote)

    // ============================== Legacy transport (pre-existing behaviour) ==============================

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
        val repository = repository(remote = remote, store = localStore)

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
    }

    @Test
    fun `restore selection hydrates local cache from connector session when local empty`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = null)
        val repository = repository(
            remote = FakeRemote(
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
            store = localStore,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals("budcom-test-01", localStore.currentId)
        assertEquals("Budcom-Test-01", (result as AppResult.Success).value?.companyName)
    }

    @Test
    fun `restore selection leaves local empty when connector session has no company`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = null)
        val repository = repository(
            remote = FakeRemote(sessionResult = ApiResult.Success(sampleSession(selectedId = null))),
            store = localStore,
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
        val repository = repository(remote = remote, store = localStore)

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Failure)
        assertEquals(null, localStore.currentId)
    }

    @Test
    fun `restore selection keeps cached ID when connector is offline`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "budcom-test-01")
        val repository = repository(
            remote = FakeRemote(selectResult = ApiResult.Failure(NetworkError.NoConnectivity)),
            store = localStore,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Offline)
        assertEquals("budcom-test-01", localStore.currentId)
    }

    @Test
    fun `restore selection keeps cached ID when session validate times out`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "budcom-test-01")
        val repository = repository(
            remote = FakeRemote(
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
            store = localStore,
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
        val repository = repository(remote = remote, store = store)

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
        repository(remote = remote, store = store).restoreSelection()
        val recreated = repository(remote = remote, store = store)

        assertEquals(SessionSelectedCompany("budcom-test-01", "Budcom-Test-01"), recreated.observeSelectedCompany().first())
        assertEquals(1, store.completeSaveCount)
    }

    @Test
    fun `unknown legacy ID remains without inheriting a name`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore("unknown-company", legacy = true)
        val repository = repository(
            remote = FakeRemote(
                selectResult = ApiResult.Success(
                    CompanySelectionOutcome("COMPANY_NOT_FOUND", sampleSession(null), "not found", 404),
                ),
            ),
            store = store,
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
        val repository = repository(remote = remote, store = store)

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
        val repository = repository(remote = remote, store = store)

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
        val repository = repository(remote = FakeRemote(selectResult = ApiResult.Failure(NetworkError.NoConnectivity)))

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
        val repository = repository(remote = remote, store = localStore)

        val result = repository.selectCompany("estimation")
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
        assertEquals("ESTIMATION", localStore.currentName)

        remote.selectResult = ApiResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("budcom-test-01"), null, 200))
        remote.validateResult = ApiResult.Success(
            SessionValidationOutcome("SUCCESS", sampleSession("budcom-test-01"), null, "budcom-test-01", "Budcom-Test-01", 200),
        )

        repository.selectCompany("budcom-test-01")
        assertEquals("budcom-test-01", localStore.currentId)
        assertEquals("Budcom-Test-01", localStore.currentName)
    }

    @Test
    fun `load companies writes cache on success`() = runTest(dispatcher) {
        val local = FakeCompanyLocal()
        val snapshot = sampleDiscovery()
        val repository = repository(remote = FakeRemote(companiesResult = ApiResult.Success(snapshot)), local = local)

        val result = repository.loadCompanies() as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals(snapshot, local.cached)
        assertEquals(1, local.replaceSnapshotCallCount)
    }

    @Test
    fun `load companies returns cache when offline`() = runTest(dispatcher) {
        val cached = sampleDiscovery()
        val local = FakeCompanyLocal(cached)
        val repository = repository(remote = FakeRemote(companiesResult = ApiResult.Failure(NetworkError.NoConnectivity)), local = local)

        val result = repository.loadCompanies() as AppResult.Success
        assertEquals("budcom-test-01", result.value.items.single().id)
    }

    @Test
    fun `load companies offline without cache fails`() = runTest(dispatcher) {
        val repository = repository(remote = FakeRemote(companiesResult = ApiResult.Failure(NetworkError.NoConnectivity)))

        val result = repository.loadCompanies() as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    // ============================== Authenticated transport routing ==============================

    @Test
    fun `load companies uses the authenticated data source and writes cache when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val local = FakeCompanyLocal()
        val snapshot = sampleDiscovery()
        val authenticated = FakeAuthenticatedRemote(companiesResult = AppResult.Success(snapshot))
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.loadCompanies() as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals(snapshot, local.cached)
        assertEquals(1, local.replaceSnapshotCallCount)
        assertEquals(1, authenticated.fetchCompaniesCallCount)
    }

    @Test
    fun `load companies falls back to cache on a non-authentication authenticated failure`() = runTest(dispatcher) {
        val cached = sampleDiscovery()
        val local = FakeCompanyLocal(cached)
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Failure(AppError.Offline())),
        )

        val result = repository.loadCompanies() as AppResult.Success
        assertEquals("budcom-test-01", result.value.items.single().id)
    }

    @Test
    fun `select company routes through the authenticated data source when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore()
        val authenticated = FakeAuthenticatedRemote(
            selectResult = AppResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = AppResult.Success(SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200)),
        )
        val repository = repository(
            remote = UnreachableRemote,
            store = localStore,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.selectCompany("estimation")
        assertTrue(result is AppResult.Success)
        assertEquals("estimation", localStore.currentId)
        assertEquals(1, authenticated.selectCompanyCallCount)
    }

    @Test
    fun `getSession routes through the authenticated data source when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val authenticated = FakeAuthenticatedRemote(sessionResult = AppResult.Success(sampleSession("estimation")))
        val repository = repository(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.getSession()
        assertTrue(result is AppResult.Success)
        assertEquals(1, authenticated.fetchSessionCallCount)
    }

    @Test
    fun `validateSession routes through the authenticated data source when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore()
        val authenticated = FakeAuthenticatedRemote(
            validateResult = AppResult.Success(SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200)),
        )
        val repository = repository(
            remote = UnreachableRemote,
            store = localStore,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.validateSession()
        assertTrue(result is AppResult.Success)
        assertEquals(1, authenticated.validateSessionCallCount)
        assertEquals("estimation", localStore.currentId)
    }

    @Test
    fun `restoreSelection routes through the authenticated data source when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "estimation")
        val authenticated = FakeAuthenticatedRemote(
            selectResult = AppResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = AppResult.Success(SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200)),
        )
        val repository = repository(
            remote = UnreachableRemote,
            store = localStore,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.restoreSelection()
        assertTrue(result is AppResult.Success)
        assertEquals(1, authenticated.selectCompanyCallCount)
        assertEquals(1, authenticated.validateSessionCallCount)
    }

    @Test
    fun `clearSelection routes through the authenticated data source when the gate resolves AUTHENTICATED`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "estimation")
        val authenticated = FakeAuthenticatedRemote(clearResult = AppResult.Success(sampleSession(null)))
        val repository = repository(
            remote = UnreachableRemote,
            store = localStore,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.clearSelection()
        assertTrue(result is AppResult.Success)
        assertEquals(null, localStore.currentId)
        assertEquals(1, authenticated.clearSessionCallCount)
    }

    @Test
    fun `every operation on the AUTHENTICATED path never reaches the legacy transport`() = runTest(dispatcher) {
        val localStore = FakeSelectedCompanyStore(initial = "estimation")
        val authenticated = FakeAuthenticatedRemote(
            selectResult = AppResult.Success(CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200)),
            validateResult = AppResult.Success(SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200)),
        )
        val repository = repository(
            remote = UnreachableRemote,
            store = localStore,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        // UnreachableRemote throws on any call — reaching the end without an exception is the proof.
        repository.loadCompanies()
        repository.getSession()
        repository.selectCompany("estimation")
        repository.validateSession()
        repository.restoreSelection()
        repository.clearSelection()
    }

    @Test
    fun `an authenticated Unpaired, PendingVerification, RePairRequired or CredentialUnavailable outcome never falls back to the legacy transport`() = runTest(dispatcher) {
        // These four vault-derived states all collapse to the same shape at the repository
        // boundary (see AuthenticatedRepositoryFailurePolicy): httpStatus null, the re-pair code.
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repository(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Failure(localVaultStateError)),
        )

        val result = repository.loadCompanies() as AppResult.Failure
        assertEquals(localVaultStateError, result.error)
    }

    @Test
    fun `every repository operation re-evaluates the transport gate on each call rather than caching it`() = runTest(dispatcher) {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repository = repository(transportGate = gate)

        repository.loadCompanies()
        repository.getSession()
        repository.selectCompany("estimation")

        assertEquals(3, gate.resolveCount)
    }

    // ============================== Phase 4: Room/cache visibility on authentication failure ==============================

    @Test
    fun `a 401-style authentication rejection surfaces the error without deleting the Room cache`() = runTest(dispatcher) {
        val cachedBefore = sampleDiscovery()
        val local = FakeCompanyLocal(cachedBefore)
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Failure(rejection)),
        )

        val result = repository.loadCompanies() as AppResult.Failure

        assertEquals(rejection, result.error)
        assertSame(cachedBefore, local.cached)
        assertEquals(0, local.replaceSnapshotCallCount)
    }

    @Test
    fun `a 403-style authentication rejection surfaces the error without deleting the Room cache`() = runTest(dispatcher) {
        val cachedBefore = sampleDiscovery()
        val local = FakeCompanyLocal(cachedBefore)
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Failure(rejection)),
        )

        val result = repository.loadCompanies() as AppResult.Failure

        assertEquals(rejection, result.error)
        assertSame(cachedBefore, local.cached)
        assertEquals(0, local.replaceSnapshotCallCount)
    }

    @Test
    fun `a failed authenticated refresh does not advance freshness metadata`() = runTest(dispatcher) {
        val cachedBefore = sampleDiscovery()
        val local = FakeCompanyLocal(cachedBefore)
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Failure(AppError.Offline())),
        )

        repository.refreshCompanies()

        assertEquals(0, local.replaceSnapshotCallCount)
        assertSame(cachedBefore, local.cached)
    }

    @Test
    fun `a successful authenticated refresh does advance freshness metadata`() = runTest(dispatcher) {
        val local = FakeCompanyLocal(sampleDiscovery())
        val freshSnapshot = sampleDiscovery().copy(dataFreshnessAt = "2026-02-01T00:00:00Z")
        val repository = repository(
            remote = UnreachableRemote,
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(companiesResult = AppResult.Success(freshSnapshot)),
        )

        val result = repository.refreshCompanies() as AppResult.Success

        assertEquals(1, local.replaceSnapshotCallCount)
        assertEquals("2026-02-01T00:00:00Z", result.value.dataFreshnessAt)
        assertEquals(freshSnapshot, local.cached)
    }

    @Test
    fun `failed authenticated selection never partially changes the selected ID or name`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore(initial = "estimation")
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repository(
            remote = UnreachableRemote,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(selectResult = AppResult.Failure(rejection)),
        )

        val result = repository.selectCompany("budcom-test-01")

        assertTrue(result is AppResult.Failure)
        assertEquals("estimation", store.currentId)
        assertEquals(0, store.completeSaveCount)
    }

    @Test
    fun `failed authenticated validation preserves the last valid local selection`() = runTest(dispatcher) {
        val store = FakeSelectedCompanyStore(initial = "estimation")
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val repository = repository(
            remote = UnreachableRemote,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(validateResult = AppResult.Failure(rejection)),
        )

        val result = repository.validateSession()

        assertTrue(result is AppResult.Failure)
        assertEquals("estimation", store.currentId)
        assertEquals(0, store.completeSaveCount)
    }
}

// ============================== Fakes ==============================

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

/** Proves a code path never reaches the legacy transport — every method throws unconditionally. */
private object UnreachableRemote : CompanyRemoteDataSource {
    override suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot> = unreachable()
    override suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot> = unreachable()
    override suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome> = unreachable()
    override suspend fun validateSession(): ApiResult<SessionValidationOutcome> = unreachable()
    override suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot> = unreachable()

    private fun unreachable(): Nothing = error("UnreachableRemote must never be called on the AUTHENTICATED path")
}

/** Proves a code path never reaches the authenticated transport — every method throws unconditionally. */
private object UnreachableAuthenticatedRemote : AuthenticatedCompanyRemoteDataSource {
    override suspend fun fetchCompanies(): AppResult<CompanyDiscoverySnapshot> = unreachable()
    override suspend fun fetchSession(): AppResult<ConnectorSessionSnapshot> = unreachable()
    override suspend fun selectCompany(companyId: String): AppResult<CompanySelectionOutcome> = unreachable()
    override suspend fun validateSession(): AppResult<SessionValidationOutcome> = unreachable()
    override suspend fun clearSession(): AppResult<ConnectorSessionSnapshot> = unreachable()

    private fun unreachable(): Nothing = error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
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
        private set
    var replaceSnapshotCallCount: Int = 0
        private set

    override suspend fun hasCache(): Boolean = cached != null

    override suspend fun readSnapshot(): CompanyDiscoverySnapshot? = cached

    override suspend fun replaceSnapshot(snapshot: CompanyDiscoverySnapshot) {
        cached = snapshot
        replaceSnapshotCallCount++
    }
}

private class FakeTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class CountingTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    var resolveCount: Int = 0
        private set

    override suspend fun resolve(): ConnectorTransportSelection {
        resolveCount++
        return selection
    }
}

private class FakeAuthenticatedRemote(
    var companiesResult: AppResult<CompanyDiscoverySnapshot> = AppResult.Success(
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
    var sessionResult: AppResult<ConnectorSessionSnapshot> = AppResult.Success(sampleSession(null)),
    var selectResult: AppResult<CompanySelectionOutcome> = AppResult.Success(
        CompanySelectionOutcome("SUCCESS", sampleSession("estimation"), null, 200),
    ),
    var validateResult: AppResult<SessionValidationOutcome> = AppResult.Success(
        SessionValidationOutcome("SUCCESS", sampleSession("estimation"), null, "estimation", "ESTIMATION", 200),
    ),
    var clearResult: AppResult<ConnectorSessionSnapshot> = AppResult.Success(sampleSession(null)),
) : AuthenticatedCompanyRemoteDataSource {
    var fetchCompaniesCallCount: Int = 0
        private set
    var fetchSessionCallCount: Int = 0
        private set
    var selectCompanyCallCount: Int = 0
        private set
    var validateSessionCallCount: Int = 0
        private set
    var clearSessionCallCount: Int = 0
        private set

    override suspend fun fetchCompanies(): AppResult<CompanyDiscoverySnapshot> {
        fetchCompaniesCallCount++
        return companiesResult
    }

    override suspend fun fetchSession(): AppResult<ConnectorSessionSnapshot> {
        fetchSessionCallCount++
        return sessionResult
    }

    override suspend fun selectCompany(companyId: String): AppResult<CompanySelectionOutcome> {
        selectCompanyCallCount++
        return selectResult
    }

    override suspend fun validateSession(): AppResult<SessionValidationOutcome> {
        validateSessionCallCount++
        return validateResult
    }

    override suspend fun clearSession(): AppResult<ConnectorSessionSnapshot> {
        clearSessionCallCount++
        return clearResult
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
