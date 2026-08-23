package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.DefaultConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryImplTest {
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
     * [transportGate]/[authenticatedRemote] — every call site must decide explicitly. Defaulting
     * both remote sources here to their Unreachable counterparts means every test that doesn't
     * override one of them also proves, for free, that the other transport is never touched.
     */
    private fun repository(
        remote: LedgerRemoteDataSource = UnreachableRemote,
        local: LedgerLocalDataSource = FakeLedgerLocal(),
        store: SelectedCompanyStore = FakeSelectedCompanyStore("co-1"),
        transportGate: ConnectorTransportSelectionGate = FakeTransportGate(ConnectorTransportSelection.LEGACY),
        authenticatedRemote: AuthenticatedLedgerRemoteDataSource = UnreachableAuthenticatedRemote,
    ): LedgerRepositoryImpl = LedgerRepositoryImpl(remote, local, store, errorMapper, dispatchers, transportGate, authenticatedRemote)

    // ============================== Legacy transport (pre-existing behaviour) ==============================

    @Test
    fun `maps success page and replaces cache`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val remote = FakeRemote(
            ApiResult.Success(
                LedgerPage(items = listOf(sampleLedger()), page = 1, pageSize = 50, totalItems = 1, totalPages = 1, dataFreshnessAt = "t"),
            ),
        )
        val repo = repository(remote = remote, local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, local.replaceCount)
        assertEquals(1, local.stored.size)
        assertEquals(1, remote.callCount)
    }

    /**
     * Live-observed defect (real-device Ledger Browser validation, 2026-08-19): a full-snapshot
     * refresh persisted every page to Room correctly, but the OLD code returned the first
     * network response's own `items` directly — a small, single-page slice in whatever order the
     * Connector happened to return it, not Room's own name-COLLATE-NOCASE sort. On a real device
     * this made an explicit Refresh visibly skip several alphabetically-earlier ledgers that a
     * normal (Room-only) open displayed correctly, since only the normal-open path re-read Room.
     * Matches `VoucherRepositoryImpl.persistAndReturn()`'s existing re-read-after-persist pattern.
     */
    @Test
    fun `refreshLedgers returns the re-read Room page reflecting the full persisted snapshot, not the raw first network page`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val remote = FakeRemote(
            ApiResult.Success(LedgerPage(listOf(sampleLedger(id = "guid:page1", name = "Aaa")), 1, 1, 2, 2, "t")),
        )
        remote.setLegacyPageResult(2, ApiResult.Success(LedgerPage(listOf(sampleLedger(id = "guid:page2", name = "Bbb")), 2, 1, 2, 2, "t2")))
        val repo = repository(remote = remote, local = local)

        val result = repo.refreshLedgers(LedgerQuery(pageSize = 1)) as AppResult.Success

        assertEquals("both pages must be persisted to Room", 2, local.stored["co-1"]!!.size)
        assertEquals(
            "the returned page must reflect Room's own re-read, not just the first network page's single item",
            2,
            result.value.items.size,
        )
    }

    @Test
    fun `offline without cache maps failure`() = runTest(dispatcher) {
        val repo = repository(remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)))

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    @Test
    fun `offline with cache returns cached page`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger()) }
        val repo = repository(remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)), local = local)

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Success
        assertEquals("guid:cash", result.value.items.single().id)
    }

    @Test
    fun `failed snapshot warm does not clear existing cache`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger(id = "guid:old", name = "Old")) }
        val remote = FakeRemote(ApiResult.Failure(NetworkError.Timeout()))
        remote.setLegacyPageResult(1, ApiResult.Success(LedgerPage(listOf(sampleLedger(id = "guid:new", name = "New")), 1, 50, 100, 2, "t2")))
        val repo = repository(remote = remote, local = local)

        repo.refreshLedgers(LedgerQuery())

        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:old" })
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:new" })
    }

    @Test
    fun `current no-secure-record installation keeps exact legacy behaviour`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val remote = FakeRemote(ApiResult.Success(LedgerPage(listOf(sampleLedger()), 1, 50, 1, 1, "t")))
        // An empty vault resolves LEGACY (proven independently by ConnectorTransportSelectionGateTest).
        val repo = repository(remote = remote, local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, remote.callCount)
    }

    // ============================== Authenticated transport routing ==============================

    @Test
    fun `ACTIVE selection routes to the authenticated adapter exactly once and never touches legacy`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(LedgerPage(listOf(sampleLedger()), 1, 50, 1, 1, "t")))
        val repo = repository(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, authenticated.callCount)
        assertEquals(1, local.replaceCount)
        assertEquals("co-1", local.stored.keys.single())
    }

    // Phase 3T-R1: the REAL gate (not a fake fixed to AUTHENTICATED) wired to a genuinely
    // Unreadable/corrupted vault must still route here — default `remote` is UnreachableRemote.
    @Test
    fun `an Unreadable vault resolved by the real transport gate never falls back to legacy`() = runTest(dispatcher) {
        val backing = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = backing)
        vault.storePendingVerification(
            "cred-1",
            "device-1",
            "raw-token",
            TrustedConnectorEndpoint.fromTrustedPublicMetadata(
                connectorId = "connector-abc",
                connectorName = "Front Desk",
                host = "10.0.0.5",
                securePort = 8443,
                transportFingerprint = "sha256/AAAA",
                fingerprintAlgorithm = "sha256",
                transportIdentityVersion = 1,
            ),
            1_000L,
        )
        backing.unreadable = true
        val authenticated = FakeAuthenticatedRemote(AppResult.Failure(AppError.Offline()))
        val repo = repository(
            transportGate = DefaultConnectorTransportSelectionGate(vault),
            authenticatedRemote = authenticated,
        )

        val result = repo.refreshLedgers(LedgerQuery())

        // UnreachableRemote (the default `remote`) throws if ever called — reaching a result at
        // all (rather than an exception) is the proof the real gate resolved AUTHENTICATED.
        assertTrue(result is AppResult.Success || result is AppResult.Failure)
        assertEquals(1, authenticated.callCount)
    }

    @Test
    fun `PENDING_VERIFICATION and RE_PAIR_REQUIRED stay authenticated and never fall back to legacy`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repo = repository(
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(localVaultStateError)),
        )

        // UnreachableRemote (the default `remote`) throws if ever called — reaching here proves it wasn't.
        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure
        assertEquals(localVaultStateError, result.error)
    }

    @Test
    fun `the transport gate is resolved on every call, not cached`() = runTest(dispatcher) {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repo = repository(remote = FakeRemote(ApiResult.Success(emptyLegacyPage())), transportGate = gate)

        repo.refreshLedgers(LedgerQuery())
        repo.refreshLedgers(LedgerQuery())

        assertEquals(2, gate.resolveCount)
    }

    @Test
    fun `a changed secure state is observed by the very next call`() = runTest(dispatcher) {
        val gate = MutableTransportGate(ConnectorTransportSelection.LEGACY)
        val legacy = FakeRemote(ApiResult.Success(emptyLegacyPage()))
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(emptyLegacyPage()))
        val repo = repository(remote = legacy, transportGate = gate, authenticatedRemote = authenticated)

        repo.refreshLedgers(LedgerQuery())
        gate.selection = ConnectorTransportSelection.AUTHENTICATED
        repo.refreshLedgers(LedgerQuery())

        assertEquals(1, legacy.callCount)
        assertEquals(1, authenticated.callCount)
    }

    // ============================== Authenticated failure / non-downgrade ==============================

    @Test
    fun `every non-authentication authenticated failure preserves cache, falls back when available, and never advances freshness`() = runTest(dispatcher) {
        val nonAuthFailures = listOf(
            AppError.Offline(), // TransportFailure
            AppError.Serialization("bad json"), // MalformedResponse
            AppError.Remote(400, "INVALID_QUERY", "bad request"), // ValidationFailure
            AppError.Remote(500, null, "server error"), // ServerFailure
            AppError.Remote(429, null, "rate limited"), // RateLimited
            AppError.Remote(409, null, "conflict"), // Conflict
            AppError.Remote(404, null, "not found"), // NotFound
            AppError.Message("cancelled"), // Cancelled
        )

        nonAuthFailures.forEach { error ->
            val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger(id = "guid:cached")) }
            val authenticated = FakeAuthenticatedRemote(AppResult.Failure(error))
            val repo = repository(
                local = local,
                transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
                authenticatedRemote = authenticated,
            )

            val result = repo.refreshLedgers(LedgerQuery())

            assertTrue("expected cache fallback for $error", result is AppResult.Success)
            assertEquals("guid:cached", (result as AppResult.Success).value.items.single().id)
            assertEquals("cache must never be replaced/cleared on failure for $error", 0, local.replaceCount)
        }
    }

    @Test
    fun `CredentialUnavailable, Unpaired, PendingVerification and RePairRequired never call legacy and never mutate Room`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger(id = "guid:cached")) }
        val repo = repository(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(localVaultStateError)),
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure

        assertEquals(localVaultStateError, result.error)
        assertEquals(0, local.replaceCount)
        assertEquals(0, local.upsertCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
    }

    // ============================== HTTP 401 and 403 ==============================

    @Test
    fun `401 never calls legacy, preserves Room and selection, and returns SecurePairingRequired`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger(id = "guid:cached")) }
        val store = WriteThrowingSelectedCompanyStore("co-1")
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(rejection)),
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure

        assertEquals(rejection, result.error)
        assertEquals(401, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
        // WriteThrowingSelectedCompanyStore would have thrown had the repository attempted any
        // selection mutation — reaching this line proves it never did.
    }

    @Test
    fun `403 never calls legacy, does not mutate credential state, preserves Room and selection, and returns AccessDenied`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger(id = "guid:cached")) }
        val store = WriteThrowingSelectedCompanyStore("co-1")
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(rejection)),
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure

        assertEquals(403, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
    }

    // ============================== Company isolation and races ==============================

    @Test
    fun `a company-A request writes only company-A rows and leaves company-B rows untouched`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-B"] = mutableListOf(sampleLedger(id = "guid:b-row")) }
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(LedgerPage(listOf(sampleLedger(id = "guid:a-row")), 1, 50, 1, 1, "t")))
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore("co-A"),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        repo.refreshLedgers(LedgerQuery())

        assertTrue(local.stored["co-A"]!!.any { it.id == "guid:a-row" })
        assertEquals(listOf("guid:b-row"), local.stored["co-B"]!!.map { it.id })
    }

    @Test
    fun `changing selected company while a request is in flight does not write that response under the new company`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val store = FakeSelectedCompanyStore("co-A")
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(LedgerPage(listOf(sampleLedger(id = "guid:a-row")), 1, 50, 1, 1, "t")))
        authenticated.beforeFetch = { store.saveSelectedCompanyId("co-B") }
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        repo.refreshLedgers(LedgerQuery())

        assertEquals("co-A", local.stored.keys.single())
        assertTrue(local.stored["co-A"]!!.any { it.id == "guid:a-row" })
        assertTrue("co-B", local.stored["co-B"].isNullOrEmpty())
    }

    @Test
    fun `a failed company-A refresh does not affect company-B rows`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-B"] = mutableListOf(sampleLedger(id = "guid:b-row")) }
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore("co-A"),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(AppError.Offline())),
        )

        repo.refreshLedgers(LedgerQuery())

        assertEquals(listOf("guid:b-row"), local.stored["co-B"]!!.map { it.id })
    }

    @Test
    fun `a blank selected-company context never triggers a write or an unsafe cache lookup`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(LedgerPage(listOf(sampleLedger()), 1, 50, 1, 1, "t")))
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore(null),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(0, local.replaceCount)
        assertEquals(0, local.upsertCount)
        assertTrue(local.stored.isEmpty())
    }

    @Test
    fun `a blank selected-company context on failure returns the mapped error without a cache lookup`() = runTest(dispatcher) {
        val repo = repository(
            store = FakeSelectedCompanyStore(null),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(AppError.Offline())),
        )

        val result = repo.refreshLedgers(LedgerQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    // ============================== Local-first ==============================

    @Test
    fun `local query never depends on or calls either remote transport`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger()) }
        val legacyCalls = intArrayOf(0)
        val authenticatedCalls = intArrayOf(0)

        // Directly exercising the local data source's own search/sort/paging query, bypassing the
        // repository's remote-refresh path entirely — the interface itself has no remote dependency.
        val page = local.query("co-1", LedgerQuery(text = "cas", sortBy = com.budcom.android.feature.masterdata.ledger.domain.model.LedgerSortBy.Name))

        assertEquals(0, legacyCalls[0])
        assertEquals(0, authenticatedCalls[0])
        assertEquals("guid:cash", page!!.items.single().id)
    }

    // ============================== listLedgers (Room-only, cache-first) ==============================

    @Test
    fun `listLedgers returns the cached page without calling either remote transport`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply { stored["co-1"] = mutableListOf(sampleLedger()) }
        // Both remote sources default to Unreachable* — reaching a Success at all is the proof
        // that listLedgers never touched either transport.
        val repo = repository(local = local)

        val result = repo.listLedgers(LedgerQuery()) as AppResult.Success

        assertEquals("guid:cash", result.value.items.single().id)
    }

    @Test
    fun `listLedgers fails honestly when the company has never been synced, without calling the network`() = runTest(dispatcher) {
        val repo = repository(local = FakeLedgerLocal())

        val result = repo.listLedgers(LedgerQuery()) as AppResult.Failure

        assertEquals(LedgerRepositoryImpl.NO_CACHE_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `listLedgers with a blank selected company fails without an unsafe cache lookup`() = runTest(dispatcher) {
        val repo = repository(local = FakeLedgerLocal(), store = FakeSelectedCompanyStore(null))

        val result = repo.listLedgers(LedgerQuery())

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `listLedgers respects the caller's search text against the cache exactly like a refresh would`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply {
            stored["co-1"] = mutableListOf(sampleLedger(id = "guid:cash", name = "Cash"), sampleLedger(id = "guid:bank", name = "Bank"))
        }
        val repo = repository(local = local)

        val result = repo.listLedgers(LedgerQuery(text = "cas")) as AppResult.Success

        assertEquals("guid:cash", result.value.items.single().id)
    }
}

// ============================== Fakes ==============================

private class FakeRemote(
    private val defaultResult: ApiResult<LedgerPage>,
) : LedgerRemoteDataSource {
    var callCount = 0
        private set
    private val pageResults = mutableMapOf<Int, ApiResult<LedgerPage>>()

    fun setLegacyPageResult(page: Int, result: ApiResult<LedgerPage>) {
        pageResults[page] = result
    }

    override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> {
        callCount++
        return pageResults[query.page] ?: defaultResult
    }

    override suspend fun fetchLedgerStatement(
        ledgerId: String,
        range: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement> =
        error("fetchLedgerStatement is not exercised by ledger-list tests")

    override suspend fun fetchLedgerContactDetails(
        ledgerId: String,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails> =
        error("fetchLedgerContactDetails is not exercised by ledger-list tests")

    override suspend fun fetchLedgerContactDetailsBulk(): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult> =
        error("fetchLedgerContactDetailsBulk is not exercised by ledger-list tests")
}

private object UnreachableRemote : LedgerRemoteDataSource {
    override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")

    override suspend fun fetchLedgerStatement(
        ledgerId: String,
        range: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement> =
        error("fetchLedgerStatement is not exercised by ledger-list tests")

    override suspend fun fetchLedgerContactDetailsBulk(): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")

    override suspend fun fetchLedgerContactDetails(
        ledgerId: String,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails> =
        error("fetchLedgerContactDetails is not exercised by ledger-list tests")
}

private class FakeAuthenticatedRemote(
    private val defaultResult: AppResult<LedgerPage>,
) : AuthenticatedLedgerRemoteDataSource {
    var callCount = 0
        private set
    var beforeFetch: suspend () -> Unit = {}
    private val pageResults = mutableMapOf<Int, AppResult<LedgerPage>>()

    fun setAuthenticatedPageResult(page: Int, result: AppResult<LedgerPage>) {
        pageResults[page] = result
    }

    override suspend fun fetchLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        callCount++
        beforeFetch()
        return pageResults[query.page] ?: defaultResult
    }
}

private object UnreachableAuthenticatedRemote : AuthenticatedLedgerRemoteDataSource {
    override suspend fun fetchLedgers(query: LedgerQuery): AppResult<LedgerPage> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
}

private class FakeTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class MutableTransportGate(var selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class CountingTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    var resolveCount = 0
        private set

    override suspend fun resolve(): ConnectorTransportSelection {
        resolveCount++
        return selection
    }
}

private class FakeLedgerLocal : LedgerLocalDataSource {
    val stored = mutableMapOf<String, MutableList<Ledger>>()
    var replaceCount = 0
        private set
    var upsertCount = 0
        private set

    override suspend fun hasCache(companyId: String): Boolean = (stored[companyId]?.isNotEmpty() == true)

    override suspend fun upsert(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
        upsertCount++
        val bucket = stored.getOrPut(companyId) { mutableListOf() }
        items.forEach { item ->
            bucket.removeAll { it.id == item.id }
            bucket.add(item)
        }
    }

    override suspend fun replaceAll(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
        replaceCount++
        stored[companyId] = items.toMutableList()
    }

    override suspend fun query(companyId: String, query: LedgerQuery): LedgerPage? {
        val items = stored[companyId] ?: return null
        if (items.isEmpty()) return null
        val filtered = query.text?.takeIf { it.isNotBlank() }?.let { text ->
            items.filter { it.name.contains(text, ignoreCase = true) }
        } ?: items
        return LedgerPage(
            items = filtered,
            page = 1,
            pageSize = query.pageSize,
            totalItems = filtered.size,
            totalPages = 1,
            dataFreshnessAt = "cached",
        )
    }
}

private class FakeSelectedCompanyStore(
    initial: String?,
) : SelectedCompanyStore {
    private val state = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = state
    override suspend fun getSelectedCompanyId(): String? = state.value
    override suspend fun saveSelectedCompanyId(companyId: String) {
        state.value = companyId
    }
    override suspend fun clearSelectedCompanyId() {
        state.value = null
    }
}

/** Throws on any selection-mutating call — used to structurally prove a code path never mutates selection. */
private class WriteThrowingSelectedCompanyStore(initial: String?) : SelectedCompanyStore {
    private val state = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = state
    override suspend fun getSelectedCompanyId(): String? = state.value
    override suspend fun saveSelectedCompanyId(companyId: String): Nothing = error("selection must not be mutated by the ledger repository")
    override suspend fun clearSelectedCompanyId(): Nothing = error("selection must not be mutated by the ledger repository")
}

private fun sampleLedger(
    id: String = "guid:cash",
    name: String = "Cash",
) = Ledger(
    id = id,
    name = name,
    alias = null,
    parentGroup = "Cash-in-Hand",
    status = LedgerStatus.Active,
    closingBalance = null,
    dataQuality = LedgerDataQuality.Complete,
    syncedAt = "t",
)

private fun emptyLegacyPage() = LedgerPage(items = emptyList(), page = 1, pageSize = 50, totalItems = 0, totalPages = 0, dataFreshnessAt = null)
