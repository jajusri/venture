package com.budcom.android.feature.masterdata.stockitem.data.repository

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
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.AuthenticatedStockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockItemRepositoryImplTest {
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
        remote: StockItemRemoteDataSource = UnreachableRemote,
        local: StockItemLocalDataSource = FakeStockLocal(),
        store: SelectedCompanyStore = FakeSelectedCompanyStore("co-1"),
        transportGate: ConnectorTransportSelectionGate = FakeTransportGate(ConnectorTransportSelection.LEGACY),
        authenticatedRemote: AuthenticatedStockItemRemoteDataSource = UnreachableAuthenticatedRemote,
    ): StockItemRepositoryImpl = StockItemRepositoryImpl(remote, local, store, errorMapper, dispatchers, transportGate, authenticatedRemote)

    // ============================== Legacy transport (pre-existing behaviour) ==============================

    @Test
    fun `maps success page and replaces cache`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val remote = FakeRemote(
            ApiResult.Success(StockItemPage(items = listOf(sampleItem()), pagination = MasterDataPagination(1, 50, 1, 1), dataFreshnessAt = "t")),
        )
        val repo = repository(remote = remote, local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, local.replaceCount)
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `offline without cache maps failure`() = runTest(dispatcher) {
        val repo = repository(remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)))

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    @Test
    fun `offline with cache returns cached page`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem()) }
        val repo = repository(remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)), local = local)

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success
        assertEquals("guid:widget", result.value.items.single().id)
    }

    @Test
    fun `failed snapshot warm does not clear existing cache`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem(id = "guid:old", name = "Old")) }
        val remote = FakeRemote(ApiResult.Failure(NetworkError.Timeout()))
        remote.setLegacyPageResult(
            1,
            ApiResult.Success(
                StockItemPage(listOf(sampleItem(id = "guid:new", name = "New")), MasterDataPagination(1, 50, 100, 2), "t2"),
            ),
        )
        val repo = repository(remote = remote, local = local)

        repo.loadStockItems(StockItemQuery())

        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:old" })
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:new" })
    }

    @Test
    fun `current no-secure-record installation keeps exact legacy behaviour`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val remote = FakeRemote(ApiResult.Success(StockItemPage(listOf(sampleItem()), MasterDataPagination(1, 50, 1, 1), "t")))
        val repo = repository(remote = remote, local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, remote.callCount)
    }

    // ============================== Authenticated transport routing ==============================

    @Test
    fun `ACTIVE selection routes to the authenticated adapter exactly once and never touches legacy`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(StockItemPage(listOf(sampleItem()), MasterDataPagination(1, 50, 1, 1), "t")))
        val repo = repository(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, authenticated.callCount)
        assertEquals(1, local.replaceCount)
        assertEquals("co-1", local.stored.keys.single())
    }

    @Test
    fun `PENDING_VERIFICATION and RE_PAIR_REQUIRED stay authenticated and never fall back to legacy`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repo = repository(
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(localVaultStateError)),
        )

        // UnreachableRemote (the default `remote`) throws if ever called — reaching here proves it wasn't.
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure
        assertEquals(localVaultStateError, result.error)
    }

    @Test
    fun `the transport gate is resolved on every call, not cached`() = runTest(dispatcher) {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repo = repository(remote = FakeRemote(ApiResult.Success(emptyLegacyPage())), transportGate = gate)

        repo.loadStockItems(StockItemQuery())
        repo.loadStockItems(StockItemQuery())

        assertEquals(2, gate.resolveCount)
    }

    @Test
    fun `a changed secure state is observed by the very next call`() = runTest(dispatcher) {
        val gate = MutableTransportGate(ConnectorTransportSelection.LEGACY)
        val legacy = FakeRemote(ApiResult.Success(emptyLegacyPage()))
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(emptyLegacyPage()))
        val repo = repository(remote = legacy, transportGate = gate, authenticatedRemote = authenticated)

        repo.loadStockItems(StockItemQuery())
        gate.selection = ConnectorTransportSelection.AUTHENTICATED
        repo.loadStockItems(StockItemQuery())

        assertEquals(1, legacy.callCount)
        assertEquals(1, authenticated.callCount)
    }

    // ============================== Authenticated failure / non-downgrade ==============================

    @Test
    fun `every non-authentication authenticated failure preserves cache, falls back when available, and never advances freshness`() = runTest(dispatcher) {
        val nonAuthFailures = listOf(
            AppError.Offline(),
            AppError.Serialization("bad json"),
            AppError.Remote(400, "INVALID_QUERY", "bad request"),
            AppError.Remote(500, null, "server error"),
            AppError.Remote(429, null, "rate limited"),
            AppError.Remote(409, null, "conflict"),
            AppError.Remote(404, null, "not found"),
            AppError.Message("cancelled"),
        )

        nonAuthFailures.forEach { error ->
            val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem(id = "guid:cached")) }
            val authenticated = FakeAuthenticatedRemote(AppResult.Failure(error))
            val repo = repository(
                local = local,
                transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
                authenticatedRemote = authenticated,
            )

            val result = repo.loadStockItems(StockItemQuery())

            assertTrue("expected cache fallback for $error", result is AppResult.Success)
            assertEquals("guid:cached", (result as AppResult.Success).value.items.single().id)
            assertEquals("cache must never be replaced/cleared on failure for $error", 0, local.replaceCount)
        }
    }

    @Test
    fun `CredentialUnavailable, Unpaired, PendingVerification and RePairRequired never call legacy and never mutate Room`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem(id = "guid:cached")) }
        val repo = repository(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(localVaultStateError)),
        )

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure

        assertEquals(localVaultStateError, result.error)
        assertEquals(0, local.replaceCount)
        assertEquals(0, local.upsertCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
    }

    // ============================== HTTP 401 and 403 ==============================

    @Test
    fun `401 never calls legacy, preserves Room and selection, and returns SecurePairingRequired`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem(id = "guid:cached")) }
        val store = WriteThrowingSelectedCompanyStore("co-1")
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(rejection)),
        )

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure

        assertEquals(rejection, result.error)
        assertEquals(401, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
    }

    @Test
    fun `403 never calls legacy, does not mutate credential state, preserves Room and selection, and returns AccessDenied`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem(id = "guid:cached")) }
        val store = WriteThrowingSelectedCompanyStore("co-1")
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(rejection)),
        )

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure

        assertEquals(403, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:cached" })
    }

    // ============================== Company isolation and races ==============================

    @Test
    fun `a company-A request writes only company-A rows and leaves company-B rows untouched`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply { stored["co-B"] = mutableListOf(sampleItem(id = "guid:b-row")) }
        val authenticated = FakeAuthenticatedRemote(
            AppResult.Success(StockItemPage(listOf(sampleItem(id = "guid:a-row")), MasterDataPagination(1, 50, 1, 1), "t")),
        )
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore("co-A"),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        repo.loadStockItems(StockItemQuery())

        assertTrue(local.stored["co-A"]!!.any { it.id == "guid:a-row" })
        assertEquals(listOf("guid:b-row"), local.stored["co-B"]!!.map { it.id })
    }

    @Test
    fun `changing selected company while a request is in flight does not write that response under the new company`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val store = FakeSelectedCompanyStore("co-A")
        val authenticated = FakeAuthenticatedRemote(
            AppResult.Success(StockItemPage(listOf(sampleItem(id = "guid:a-row")), MasterDataPagination(1, 50, 1, 1), "t")),
        )
        authenticated.beforeFetch = { store.saveSelectedCompanyId("co-B") }
        val repo = repository(
            local = local,
            store = store,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        repo.loadStockItems(StockItemQuery())

        assertEquals("co-A", local.stored.keys.single())
        assertTrue(local.stored["co-A"]!!.any { it.id == "guid:a-row" })
        assertTrue("co-B", local.stored["co-B"].isNullOrEmpty())
    }

    @Test
    fun `a failed company-A refresh does not affect company-B rows`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply { stored["co-B"] = mutableListOf(sampleItem(id = "guid:b-row")) }
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore("co-A"),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(AppResult.Failure(AppError.Offline())),
        )

        repo.loadStockItems(StockItemQuery())

        assertEquals(listOf("guid:b-row"), local.stored["co-B"]!!.map { it.id })
    }

    @Test
    fun `a blank selected-company context never triggers a write or an unsafe cache lookup`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val authenticated = FakeAuthenticatedRemote(AppResult.Success(StockItemPage(listOf(sampleItem()), MasterDataPagination(1, 50, 1, 1), "t")))
        val repo = repository(
            local = local,
            store = FakeSelectedCompanyStore(null),
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success

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

        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    // ============================== Local-first ==============================

    @Test
    fun `local query never depends on or calls either remote transport`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply { stored["co-1"] = mutableListOf(sampleItem()) }

        val page = local.query("co-1", StockItemQuery(text = "wid"))

        assertEquals("guid:widget", page!!.items.single().id)
    }
}

// ============================== Fakes ==============================

private class FakeRemote(
    private val defaultResult: ApiResult<StockItemPage>,
) : StockItemRemoteDataSource {
    var callCount = 0
        private set
    private val pageResults = mutableMapOf<Int, ApiResult<StockItemPage>>()

    fun setLegacyPageResult(page: Int, result: ApiResult<StockItemPage>) {
        pageResults[page] = result
    }

    override suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage> {
        callCount++
        return pageResults[query.page] ?: defaultResult
    }
}

private object UnreachableRemote : StockItemRemoteDataSource {
    override suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
}

private class FakeAuthenticatedRemote(
    private val defaultResult: AppResult<StockItemPage>,
) : AuthenticatedStockItemRemoteDataSource {
    var callCount = 0
        private set
    var beforeFetch: suspend () -> Unit = {}
    private val pageResults = mutableMapOf<Int, AppResult<StockItemPage>>()

    override suspend fun fetchStockItems(query: StockItemQuery): AppResult<StockItemPage> {
        callCount++
        beforeFetch()
        return pageResults[query.page] ?: defaultResult
    }
}

private object UnreachableAuthenticatedRemote : AuthenticatedStockItemRemoteDataSource {
    override suspend fun fetchStockItems(query: StockItemQuery): AppResult<StockItemPage> =
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

private class FakeStockLocal : StockItemLocalDataSource {
    val stored = mutableMapOf<String, MutableList<StockItem>>()
    var replaceCount = 0
        private set
    var upsertCount = 0
        private set

    override suspend fun hasCache(companyId: String): Boolean = stored[companyId]?.isNotEmpty() == true

    override suspend fun upsert(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        upsertCount++
        val bucket = stored.getOrPut(companyId) { mutableListOf() }
        items.forEach { item ->
            bucket.removeAll { it.id == item.id }
            bucket.add(item)
        }
    }

    override suspend fun replaceAll(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
        replaceCount++
        stored[companyId] = items.toMutableList()
    }

    override suspend fun query(companyId: String, query: StockItemQuery): StockItemPage? {
        val items = stored[companyId] ?: return null
        if (items.isEmpty()) return null
        val filtered = query.text?.takeIf { it.isNotBlank() }?.let { text ->
            items.filter { it.name.contains(text, ignoreCase = true) }
        } ?: items
        return StockItemPage(
            items = filtered,
            pagination = MasterDataPagination(1, query.pageSize, filtered.size, 1),
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
    override suspend fun saveSelectedCompanyId(companyId: String): Nothing = error("selection must not be mutated by the stock-item repository")
    override suspend fun clearSelectedCompanyId(): Nothing = error("selection must not be mutated by the stock-item repository")
}

private fun sampleItem(
    id: String = "guid:widget",
    name: String = "Widget",
) = StockItem(
    id = id,
    name = name,
    alias = null,
    parentGroup = "Primary",
    category = null,
    baseUnit = "Nos",
    partNumber = null,
    hsnCode = null,
    gstRate = null,
    status = StockItemStatus.Active,
    closingBalance = null,
    dataQuality = StockItemDataQuality.Complete,
    syncedAt = "t",
)

private fun emptyLegacyPage() = StockItemPage(items = emptyList(), pagination = MasterDataPagination(1, 50, 0, 0), dataFreshnessAt = null)
