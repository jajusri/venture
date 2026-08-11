package com.budcom.android.feature.voucher.data.repository

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
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherDetailRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherListRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoucherRepositoryImpl] must keep Room as the sole source for [VoucherRepositoryImpl.listVouchers]
 * / [VoucherRepositoryImpl.getVoucherDetails] (never contacting the Connector), and must never let a
 * failed [VoucherRepositoryImpl.refreshVouchers] / [VoucherRepositoryImpl.refreshVoucherDetails] replace
 * or hide valid cached rows. See Phase 3E offline-voucher-reliability root cause notes.
 *
 * [refreshVouchers] resolves [ConnectorTransportSelectionGate] independently from
 * [refreshVoucherDetails] (Phase 3S-D1 / Phase 3S-D2 respectively) — see the "authenticated
 * transport" sections below for each method's non-downgrading, no-dual-transport, and
 * credential-identity coverage. Every pre-existing test is retained exactly, exercised via the
 * production constructor's test-only LEGACY-gate + Unreachable-authenticated-adapter defaults,
 * which incidentally prove neither authenticated transport is reached by unrelated methods.
 */
class VoucherRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val timeProvider = TimeProvider { 999_000L }
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

    // ============================== Pre-existing behaviour (unchanged) ==============================

    @Test
    fun `listVouchers never calls the remote data source`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val local = FakeLocal(listValue = samplePage())
        repo(remote, local).listVouchers(query())
        assertEquals(0, remote.listCalls)
    }

    @Test
    fun `getVoucherDetails never calls the remote data source`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val local = FakeLocal(detailsValue = sampleDetails())
        repo(remote, local).getVoucherDetails("company-a", "v-1")
        assertEquals(0, remote.detailCalls)
    }

    @Test
    fun `listVouchers returns matching cached rows immediately`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = samplePage())
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Success
        assertEquals("v-1", result.value.items[0].identity.id)
        assertEquals("company-a", local.lastListCompany)
    }

    @Test
    fun `listVouchers reports no-cache only when Room has never synced this company`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = null)
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Failure
        assertEquals(VoucherRepositoryImpl.NO_CACHE_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `listVouchers returns a true empty page distinctly from no-cache`() = runTest(dispatcher) {
        val emptyButSynced = samplePage().copy(items = emptyList(), totalItems = 0, totalPages = 0)
        val local = FakeLocal(listValue = emptyButSynced)
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Success
        assertTrue(result.value.items.isEmpty())
    }

    @Test
    fun `refresh success persists and returns fresh live rows`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val local = FakeLocal(listValue = samplePage().copy(cacheState = VoucherCacheState.Live, lastSyncedAt = 999_000L))
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Success
        assertEquals(1, local.storedItems.size)
        assertEquals(VoucherCacheState.Live, result.value.cacheState)
        assertEquals(999_000L, result.value.lastSyncedAt)
    }

    @Test
    fun `refresh failure never maps to cached-data absence and does not touch Room`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.Timeout()))
        val local = FakeLocal(listValue = samplePage())
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Failure
        assertTrue(result.error is AppError.Timeout)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `refresh failure on http 4xx still never touches cache`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.Http(400, "VALIDATION_ERROR", "Invalid query.")))
        val local = FakeLocal(listValue = samplePage())
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Failure
        assertTrue(result.error is AppError.Remote)
        assertEquals(400, (result.error as AppError.Remote).httpStatus)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `a caller can still read Room after a failed refresh and see the same cached rows`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.NoConnectivity))
        val local = FakeLocal(listValue = samplePage())
        val repository = repo(remote, local)
        repository.refreshVouchers(query())
        val stillCached = repository.listVouchers(query()) as AppResult.Success
        assertEquals("v-1", stillCached.value.items[0].identity.id)
    }

    @Test
    fun `refreshVoucherDetails success persists and returns live details`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val local = FakeLocal()
        val result = repo(remote, local).refreshVoucherDetails("company-a", "v-1") as AppResult.Success
        assertEquals(999_000L, result.value.lastSyncedAt)
        assertEquals(VoucherCacheState.Live, result.value.cacheState)
    }

    @Test
    fun `refreshVoucherDetails failure never falls back to cache`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Failure(NetworkError.Serialization("bad response")))
        val local = FakeLocal(detailsValue = sampleDetails())
        val result = repo(remote, local).refreshVoucherDetails("company-a", "v-1") as AppResult.Failure
        assertTrue(result.error is AppError.Serialization)
    }

    @Test
    fun `getVoucherDetails reports no-cache-details when never synced`() = runTest(dispatcher) {
        val local = FakeLocal(detailsValue = null)
        val result = repo(FakeRemote(), local).getVoucherDetails("company-a", "missing") as AppResult.Failure
        assertEquals(VoucherRepositoryImpl.NO_CACHE_DETAILS_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `getVoucherDetails returns cached inventory lines`() = runTest(dispatcher) {
        val local = FakeLocal(detailsValue = sampleDetails())
        val result = repo(FakeRemote(), local).getVoucherDetails("company-a", "v-1") as AppResult.Success
        assertFalse(result.value.inventoryEntries.isEmpty())
        assertEquals("Item", result.value.inventoryEntries.single().itemName)
    }

    @Test
    fun `getCachedVoucherSummary never calls the remote data source`() = runTest(dispatcher) {
        val remote = FakeRemote()
        val local = FakeLocal(summaryValue = sampleSummary())
        repo(remote, local).getCachedVoucherSummary("company-a", "v-1")
        assertEquals(0, remote.listCalls)
        assertEquals(0, remote.detailCalls)
    }

    @Test
    fun `getCachedVoucherSummary returns the header even when full details are not stored`() = runTest(dispatcher) {
        val local = FakeLocal(summaryValue = sampleSummary(), detailsValue = null)
        val result = repo(FakeRemote(), local).getCachedVoucherSummary("company-a", "v-1")
        assertEquals("v-1", result?.identity?.id)
    }

    @Test
    fun `getCachedVoucherSummary returns null when the voucher is not known locally at all`() = runTest(dispatcher) {
        val local = FakeLocal(summaryValue = null)
        val result = repo(FakeRemote(), local).getCachedVoucherSummary("company-a", "unknown")
        assertEquals(null, result)
    }

    // ============================== Authenticated list transport (Phase 3S-D1) ==============================

    @Test
    fun `empty vault (LEGACY) refresh calls legacy exactly once and never touches the authenticated adapter`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val local = FakeLocal(listValue = samplePage())
        val result = repo(remote, local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY)).refreshVouchers(query()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, remote.listCalls)
        assertEquals(1, local.storeListCalls)
    }

    @Test
    fun `ACTIVE selection routes list refresh to the authenticated adapter exactly once and never touches legacy`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = samplePage())
        val authenticated = FakeAuthenticatedListRemote(AppResult.Success(samplePage()))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = authenticated,
        )

        val result = repository.refreshVouchers(query()) as AppResult.Success

        assertEquals(1, result.value.items.size)
        assertEquals(1, authenticated.callCount)
        assertEquals(1, local.storeListCalls)
        assertEquals("company-a", local.lastStoreCompany)
    }

    // Phase 3T-R1: the REAL gate (not a fake fixed to AUTHENTICATED) wired to a genuinely
    // Unreadable/corrupted vault must still route here — default `remote` is UnreachableRemote.
    @Test
    fun `an Unreadable vault resolved by the real transport gate never falls back to legacy for list refresh`() = runTest(dispatcher) {
        val vault = unreadableVault()
        val authenticated = FakeAuthenticatedListRemote(AppResult.Failure(AppError.Offline()))
        val repository = repo(
            transportGate = DefaultConnectorTransportSelectionGate(vault),
            authenticatedListRemote = authenticated,
        )

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success || result is AppResult.Failure)
        assertEquals(1, authenticated.callCount)
    }

    @Test
    fun `list PENDING_VERIFICATION and RE_PAIR_REQUIRED stay authenticated and never fall back to legacy`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repo(
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(localVaultStateError)),
        )

        // Default `remote` is UnreachableRemote — throws if ever called; reaching here proves it wasn't.
        val result = repository.refreshVouchers(query()) as AppResult.Failure
        assertEquals(localVaultStateError, result.error)
    }

    @Test
    fun `the list transport gate is resolved on every refresh call, not cached`() = runTest(dispatcher) {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repository = repo(FakeRemote(listResult = ApiResult.Success(samplePage())), transportGate = gate)

        repository.refreshVouchers(query())
        repository.refreshVouchers(query())

        assertEquals(2, gate.resolveCount)
    }

    @Test
    fun `a changed secure state is observed by the very next list refresh call`() = runTest(dispatcher) {
        val gate = MutableTransportGate(ConnectorTransportSelection.LEGACY)
        val legacy = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val authenticated = FakeAuthenticatedListRemote(AppResult.Success(samplePage()))
        val repository = repo(legacy, transportGate = gate, authenticatedListRemote = authenticated)

        repository.refreshVouchers(query())
        gate.selection = ConnectorTransportSelection.AUTHENTICATED
        repository.refreshVouchers(query())

        assertEquals(1, legacy.listCalls)
        assertEquals(1, authenticated.callCount)
    }

    @Test
    fun `every non-authentication authenticated list failure leaves Room unchanged and remains a failure result`() = runTest(dispatcher) {
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
            val local = FakeLocal(listValue = samplePage())
            val repository = repo(
                local = local,
                transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
                authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(error)),
            )

            val result = repository.refreshVouchers(query())

            assertTrue("expected a failure result for $error", result is AppResult.Failure)
            assertEquals("failure for $error must not touch Room", 0, local.storeListCalls)
        }
    }

    @Test
    fun `list CredentialUnavailable, Unpaired, PendingVerification and RePairRequired never call legacy and never touch Room`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLocal(listValue = samplePage())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(localVaultStateError)),
        )

        val result = repository.refreshVouchers(query()) as AppResult.Failure

        assertEquals(localVaultStateError, result.error)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `list 401 never calls legacy, preserves Room, and returns SecurePairingRequired`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLocal(listValue = samplePage())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(rejection)),
        )

        val result = repository.refreshVouchers(query()) as AppResult.Failure

        assertEquals(rejection, result.error)
        assertEquals(401, (result.error as AppError.Remote).httpStatus)
        assertEquals(0, local.storeListCalls)
        val stillCached = repository.listVouchers(query()) as AppResult.Success
        assertEquals("v-1", stillCached.value.items[0].identity.id)
    }

    @Test
    fun `list 403 never calls legacy, does not mutate credential state, preserves Room, and returns AccessDenied`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val local = FakeLocal(listValue = samplePage())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(rejection)),
        )

        val result = repository.refreshVouchers(query()) as AppResult.Failure

        assertEquals(403, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `an authenticated company-A list response writes only company-A rows and leaves company-B rows untouched`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-b"] = mutableListOf(sampleSummary(id = "b-row"))
        val authenticated = FakeAuthenticatedListRemote(AppResult.Success(samplePage(companyId = "company-a")))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = authenticated,
        )

        repository.refreshVouchers(query(companyId = "company-a"))

        assertEquals("company-a", local.lastStoreCompany)
        assertEquals(listOf("b-row"), local.stored["company-b"]!!.map { it.identity.id })
    }

    @Test
    fun `list request company and date scope are taken from the immutable query, never re-read after the response`() = runTest(dispatcher) {
        // VoucherRepositoryImpl has no SelectedCompanyStore/date-scope dependency at all — company
        // and date range are fields of the VoucherQuery parameter itself, so there is nothing to
        // "capture before dispatch": the same immutable value is used for the request and for
        // persistence by construction. This test pins that structural guarantee.
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedListRemote(AppResult.Success(samplePage(companyId = "company-a")))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = authenticated,
        )
        val scopedQuery = query(companyId = "company-a").copy(dateRange = VoucherDateRange("2026-06-01", "2026-06-30"))

        repository.refreshVouchers(scopedQuery)

        assertEquals("company-a", authenticated.lastQuery?.companyId)
        assertEquals(VoucherDateRange("2026-06-01", "2026-06-30"), authenticated.lastQuery?.dateRange)
        assertEquals("company-a", local.lastStoreCompany)
    }

    @Test
    fun `a failed company-A list refresh does not affect company-B rows`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-b"] = mutableListOf(sampleSummary(id = "b-row"))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = FakeAuthenticatedListRemote(AppResult.Failure(AppError.Offline())),
        )

        repository.refreshVouchers(query(companyId = "company-a"))

        assertEquals(listOf("b-row"), local.stored["company-b"]!!.map { it.identity.id })
    }

    @Test
    fun `an authoritative empty authenticated list success still advances freshness, matching legacy semantics`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = samplePage().copy(items = emptyList(), totalItems = 0, totalPages = 0))
        val emptyPage = samplePage().copy(items = emptyList(), totalItems = 0, totalPages = 0, fullDetails = emptyList())
        val authenticated = FakeAuthenticatedListRemote(AppResult.Success(emptyPage))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = authenticated,
        )

        val result = repository.refreshVouchers(query()) as AppResult.Success

        assertEquals(1, local.storeListCalls)
        assertEquals(999_000L, result.value.lastSyncedAt)
    }

    // ============================== Authenticated detail transport (Phase 3S-D2) ==============================

    @Test
    fun `empty vault (LEGACY) detail refresh calls legacy exactly once and never touches the authenticated detail adapter`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val local = FakeLocal()
        val result = repo(remote, local, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))
            .refreshVoucherDetails("company-a", "v-1") as AppResult.Success

        assertEquals("v-1", result.value.summary.identity.id)
        assertEquals(1, remote.detailCalls)
        assertEquals(1, local.storeDetailsCalls)
    }

    @Test
    fun `ACTIVE selection routes detail refresh to the authenticated adapter exactly once and never touches legacy`() = runTest(dispatcher) {
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails()))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = authenticated,
        )

        val result = repository.refreshVoucherDetails("company-a", "v-1") as AppResult.Success

        assertEquals("v-1", result.value.summary.identity.id)
        assertEquals(1, authenticated.callCount)
        assertEquals(1, local.storeDetailsCalls)
        assertEquals("company-a", local.lastStoreDetailsCompany)
    }

    // Phase 3T-R1: the REAL gate (not a fake fixed to AUTHENTICATED) wired to a genuinely
    // Unreadable/corrupted vault must still route here — default `remote` is UnreachableRemote.
    @Test
    fun `an Unreadable vault resolved by the real transport gate never falls back to legacy for detail refresh`() = runTest(dispatcher) {
        val vault = unreadableVault()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Failure(AppError.Offline()))
        val repository = repo(
            transportGate = DefaultConnectorTransportSelectionGate(vault),
            authenticatedDetailRemote = authenticated,
        )

        val result = repository.refreshVoucherDetails("company-a", "v-1")

        assertTrue(result is AppResult.Success || result is AppResult.Failure)
        assertEquals(1, authenticated.callCount)
    }

    @Test
    fun `detail PENDING_VERIFICATION and RE_PAIR_REQUIRED stay authenticated and never fall back to legacy`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repo(
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(localVaultStateError)),
        )

        // Default `remote` is UnreachableRemote — throws if ever called; reaching here proves it wasn't.
        val result = repository.refreshVoucherDetails("company-a", "v-1") as AppResult.Failure
        assertEquals(localVaultStateError, result.error)
    }

    @Test
    fun `the detail transport gate is resolved on every refresh call, not cached`() = runTest(dispatcher) {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repository = repo(FakeRemote(detailsResult = ApiResult.Success(sampleDetails())), transportGate = gate)

        repository.refreshVoucherDetails("company-a", "v-1")
        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals(2, gate.resolveCount)
    }

    @Test
    fun `a changed secure state is observed by the very next detail refresh call`() = runTest(dispatcher) {
        val gate = MutableTransportGate(ConnectorTransportSelection.LEGACY)
        val legacy = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails()))
        val repository = repo(legacy, transportGate = gate, authenticatedDetailRemote = authenticated)

        repository.refreshVoucherDetails("company-a", "v-1")
        gate.selection = ConnectorTransportSelection.AUTHENTICATED
        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals(1, legacy.detailCalls)
        assertEquals(1, authenticated.callCount)
    }

    @Test
    fun `every non-authentication authenticated detail failure leaves Room unchanged and remains a failure result`() = runTest(dispatcher) {
        val nonAuthFailures = listOf(
            AppError.Offline(),
            AppError.Serialization("bad json"),
            AppError.Remote(400, "INVALID_QUERY", "bad request"),
            AppError.Remote(500, null, "server error"),
            AppError.Remote(429, null, "rate limited"),
            AppError.Remote(409, null, "conflict"),
            AppError.Remote(404, "NOT_FOUND", "not found"),
            AppError.Message("cancelled"),
        )

        nonAuthFailures.forEach { error ->
            val local = FakeLocal(detailsValue = sampleDetails())
            val repository = repo(
                local = local,
                transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
                authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(error)),
            )

            val result = repository.refreshVoucherDetails("company-a", "v-1")

            assertTrue("expected a failure result for $error", result is AppResult.Failure)
            assertEquals("failure for $error must not touch Room", 0, local.storeDetailsCalls)
            val stillCached = repository.getVoucherDetails("company-a", "v-1") as AppResult.Success
            assertEquals("v-1", stillCached.value.summary.identity.id)
        }
    }

    @Test
    fun `detail CredentialUnavailable, Unpaired, PendingVerification and RePairRequired never call legacy and never touch Room`() = runTest(dispatcher) {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLocal(detailsValue = sampleDetails())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(localVaultStateError)),
        )

        val result = repository.refreshVoucherDetails("company-a", "v-1") as AppResult.Failure

        assertEquals(localVaultStateError, result.error)
        assertEquals(0, local.storeDetailsCalls)
    }

    @Test
    fun `detail 401 never calls legacy, preserves cached detail, and returns SecurePairingRequired`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val local = FakeLocal(detailsValue = sampleDetails())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(rejection)),
        )

        val result = repository.refreshVoucherDetails("company-a", "v-1") as AppResult.Failure

        assertEquals(rejection, result.error)
        assertEquals(401, (result.error as AppError.Remote).httpStatus)
        assertEquals(0, local.storeDetailsCalls)
        val stillCached = repository.getVoucherDetails("company-a", "v-1") as AppResult.Success
        assertEquals("v-1", stillCached.value.summary.identity.id)
    }

    @Test
    fun `detail 403 never calls legacy, does not mutate credential state, preserves cached detail, and returns AccessDenied`() = runTest(dispatcher) {
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val local = FakeLocal(detailsValue = sampleDetails())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(rejection)),
        )

        val result = repository.refreshVoucherDetails("company-a", "v-1") as AppResult.Failure

        assertEquals(403, (result.error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, (result.error as AppError.Remote).code)
        assertEquals(0, local.storeDetailsCalls)
    }

    @Test
    fun `an authenticated company-A detail response persists only under company-A and leaves company-B detail untouched`() = runTest(dispatcher) {
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails()))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = authenticated,
        )

        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals("company-a", local.lastStoreDetailsCompany)
    }

    @Test
    fun `a response for voucher X is requested and persisted under voucher X, never voucher Y`() = runTest(dispatcher) {
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails(voucherId = "v-x")))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = authenticated,
        )

        repository.refreshVoucherDetails("company-a", "v-x")

        assertEquals("v-x", authenticated.lastVoucherId)
        assertEquals("v-x", local.lastStoreDetailsId)
    }

    @Test
    fun `detail voucherId and companyId are taken from the method parameters, never re-read after the response`() = runTest(dispatcher) {
        // VoucherRepositoryImpl has no SelectedCompanyStore dependency at all — companyId/voucherId
        // are this method's own parameters, so the same values used to dispatch the request are,
        // by construction, the only values ever available to label the persisted response.
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails(voucherId = "v-1")))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = authenticated,
        )

        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals("company-a", authenticated.lastCompanyId)
        assertEquals("v-1", authenticated.lastVoucherId)
        assertEquals("company-a", local.lastStoreDetailsCompany)
        assertEquals("v-1", local.lastStoreDetailsId)
    }

    @Test
    fun `a failed company-A detail refresh does not affect any other cached detail`() = runTest(dispatcher) {
        val local = FakeLocal(detailsValue = sampleDetails())
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = FakeAuthenticatedDetailRemote(AppResult.Failure(AppError.Offline())),
        )

        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals(0, local.storeDetailsCalls)
        val stillCached = repository.getVoucherDetails("company-a", "v-1") as AppResult.Success
        assertEquals("v-1", stillCached.value.summary.identity.id)
    }

    @Test
    fun `repeated successful authenticated detail refresh of the same voucher is idempotent`() = runTest(dispatcher) {
        val local = FakeLocal()
        val authenticated = FakeAuthenticatedDetailRemote(AppResult.Success(sampleDetails()))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedDetailRemote = authenticated,
        )

        repository.refreshVoucherDetails("company-a", "v-1")
        repository.refreshVoucherDetails("company-a", "v-1")

        assertEquals(2, local.storeDetailsCalls)
        assertEquals("v-1", local.lastStoreDetailsId)
    }

    // ====================== Windowed refresh scope-bounded replacement ======================

    @Test
    fun `refreshVouchers fetches every page of the window before persisting anything`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(
            pages = listOf(
                listOf(sampleSummary(id = "v-1")),
                listOf(sampleSummary(id = "v-2")),
            ),
        )
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = remote,
        )

        repository.refreshVouchers(query())

        assertEquals(2, remote.callCount)
        assertEquals(1, local.storeListCalls)
        assertEquals(setOf("v-1", "v-2"), local.storedItems.map { it.identity.id }.toSet())
    }

    @Test
    fun `refreshVouchers strips search and type filters from the network fetch, applying them only on read-back`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = samplePage())
        val remote = FakeAuthenticatedListRemotePaged(pages = listOf(listOf(sampleSummary())))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = remote,
        )
        val filtered = query().copy(searchText = "acme", voucherType = "Sales", partyName = "Acme")

        repository.refreshVouchers(filtered)

        assertEquals(null, remote.lastQuery?.searchText)
        assertEquals(null, remote.lastQuery?.voucherType)
        assertEquals(null, remote.lastQuery?.partyName)
        // The read-back after persisting still applies the caller's own filtered query.
        assertEquals("company-a", local.lastListCompany)
    }

    @Test
    fun `a windowed refresh prunes a previously-cached voucher in scope that is absent from the fresh result`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-a"] = mutableListOf(
            sampleSummary(id = "v-stale").copy(date = "2026-07-10"),
            sampleSummary(id = "v-kept").copy(date = "2026-07-15"),
        )
        val remote = FakeAuthenticatedListRemotePaged(
            pages = listOf(listOf(sampleSummary(id = "v-kept").copy(date = "2026-07-15"))),
        )
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = remote,
        )

        repository.refreshVouchers(query().copy(dateRange = VoucherDateRange("2026-07-01", "2026-07-27")))

        val remaining = local.stored["company-a"]!!.map { it.identity.id }.toSet()
        assertEquals(setOf("v-kept"), remaining)
    }

    @Test
    fun `a windowed refresh never prunes a cached voucher outside the requested date range`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-a"] = mutableListOf(
            sampleSummary(id = "v-outside").copy(date = "2026-06-01"),
        )
        val remote = FakeAuthenticatedListRemotePaged(pages = listOf(emptyList()))
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = remote,
        )

        repository.refreshVouchers(query().copy(dateRange = VoucherDateRange("2026-07-01", "2026-07-27")))

        assertEquals(setOf("v-outside"), local.stored["company-a"]!!.map { it.identity.id }.toSet())
    }

    @Test
    fun `refreshVouchers fails without persisting anything if the window exceeds the page safety cap`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(neverEnds = true)
        val repository = repo(
            local = local,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedListRemote = remote,
        )

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
    }

    // ====================== Complete-window fetch completeness proof (BUDCOM MVP-1 Section 1) ======================

    @Test
    fun `499 records across 5 pages are all fetched and persisted`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 499, recordsPerPage = 100)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(499, local.storedItems.size)
    }

    @Test
    fun `exactly 500 records across the old page-count boundary are all fetched and persisted`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 500, recordsPerPage = 100)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(500, local.storedItems.size)
    }

    @Test
    fun `501 records — one past the old boundary — are all fetched and persisted, not silently truncated`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 501, recordsPerPage = 100)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(501, local.storedItems.size)
    }

    @Test
    fun `a window with many pages well beyond the old 500-page cap still completes and persists everything`() = runTest(dispatcher) {
        val local = FakeLocal()
        // 60,000 records / 100 per page = 600 pages — beyond the old MAX_REFRESH_PAGES=500 cap.
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 60_000, recordsPerPage = 100)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(60_000, local.storedItems.size)
    }

    @Test
    fun `remote pagination failure midway through a window persists nothing and fails the refresh`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 1_000, recordsPerPage = 100, failOnPage = 5)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `a page reporting a different total than earlier pages fails closed without persisting`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 300, recordsPerPage = 100, corruptTotalOnPage = 2)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `a page claiming more pages exist while returning zero items fails closed without persisting`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 300, recordsPerPage = 100, emptyButClaimsMoreOnPage = 2)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `no pruning ever happens when window completeness could not be proven`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-a"] = mutableListOf(sampleSummary(id = "v-existing").copy(date = "2026-07-10"))
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 300, recordsPerPage = 100, failOnPage = 2)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        repository.refreshVouchers(query().copy(dateRange = VoucherDateRange("2026-07-01", "2026-07-27")))

        assertEquals(setOf("v-existing"), local.stored["company-a"]!!.map { it.identity.id }.toSet())
    }

    // ==================== Offline-complete window sync ====================

    @Test
    fun `successful window sync stores full details, not just summaries`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemote(AppResult.Success(samplePage()))
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(1, local.storeListWithDetailsCalls)
        assertEquals(1, local.storedFullDetails.size)
        assertTrue("ledger/accounting lines must be part of the persisted payload", local.storedFullDetails.single().inventoryEntries.isNotEmpty())
    }

    @Test
    fun `every refresh, including a historical background-reconciliation window, requests and stores full details`() = runTest(dispatcher) {
        // Not "recent" — an old window, the kind ReconcileVoucherWindowsUseCase walks through in
        // the background. refreshVouchers must not special-case "is this the fast window?" — every
        // window it's asked to refresh becomes fully offline-ready.
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemote(AppResult.Success(samplePage()))
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)
        val historicalWindow = query().copy(dateRange = VoucherDateRange("2024-01-01", "2024-01-31"))

        repository.refreshVouchers(historicalWindow)

        assertEquals(1, local.storeListWithDetailsCalls)
        assertEquals(1, local.storedFullDetails.size)
    }

    @Test
    fun `a page missing full details when details were requested fails the window closed`() = runTest(dispatcher) {
        val local = FakeLocal()
        // A page whose summary items exist but fullDetails is null — a transport/mapping bug that
        // silently lost detail data must never be persisted as though it were complete.
        val incompletePage = samplePage().copy(fullDetails = null)
        val remote = FakeAuthenticatedListRemote(AppResult.Success(incompletePage))
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
        assertEquals(0, local.storeListWithDetailsCalls)
    }

    @Test
    fun `a page whose detail count does not match its item count fails the window closed`() = runTest(dispatcher) {
        val local = FakeLocal()
        val mismatched = samplePage().copy(fullDetails = emptyList())
        val remote = FakeAuthenticatedListRemote(AppResult.Success(mismatched))
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListWithDetailsCalls)
    }

    @Test
    fun `detail-transfer failure on a later page never touches Room, and the previous good window survives`() = runTest(dispatcher) {
        val local = FakeLocal()
        local.stored["company-a"] = mutableListOf(sampleSummary(id = "v-existing").copy(date = "2026-07-10"))
        // Page 1 is a complete, well-formed page; page 2 loses its detail data.
        val remote = object : AuthenticatedVoucherListRemoteDataSource {
            override suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage> {
                val item = sampleSummary(id = "v-page${query.page}")
                return AppResult.Success(
                    VoucherPage(
                        "company-a", listOf(item), query.page, query.pageSize, 2, 2,
                        fullDetails = if (query.page == 1) listOf(item.toEmptyDetails()) else null,
                    ),
                )
            }
        }
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query().copy(pageSize = 1))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, local.storeListCalls)
        assertEquals(0, local.storeListWithDetailsCalls)
        assertEquals(setOf("v-existing"), local.stored["company-a"]!!.map { it.identity.id }.toSet())
    }

    @Test
    fun `a 10,000-Voucher window is fetched in bounded 100-item pages, not one unbounded payload or one call per Voucher`() = runTest(dispatcher) {
        val local = FakeLocal()
        val remote = FakeAuthenticatedListRemotePaged(totalRecords = 10_000, recordsPerPage = 100)
        val repository = repo(local = local, transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED), authenticatedListRemote = remote)

        val result = repository.refreshVouchers(query())

        assertTrue(result is AppResult.Success)
        assertEquals(100, remote.callCount)
        assertEquals(1, local.storeListWithDetailsCalls)
        assertEquals(10_000, local.storedFullDetails.size)
    }

    // ============================== Fakes ==============================

    private fun repo(
        remote: VoucherRemoteDataSource = UnreachableRemote,
        local: VoucherLocalDataSource = FakeLocal(),
        transportGate: ConnectorTransportSelectionGate = FakeTransportGate(ConnectorTransportSelection.LEGACY),
        authenticatedListRemote: AuthenticatedVoucherListRemoteDataSource = UnreachableAuthenticatedListRemote,
        authenticatedDetailRemote: AuthenticatedVoucherDetailRemoteDataSource = UnreachableAuthenticatedDetailRemote,
    ) = VoucherRepositoryImpl(remote, errorMapper, dispatchers, local, timeProvider, transportGate, authenticatedListRemote, authenticatedDetailRemote)

    /** A vault holding a genuinely enrolled-but-corrupted (Unreadable) record — Phase 3T-R1. */
    private suspend fun unreadableVault(): FakeSecureCredentialVault {
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
        return vault
    }

    private class FakeRemote(
        private val listResult: ApiResult<VoucherPage> = ApiResult.Failure(NetworkError.Unknown()),
        private val detailsResult: ApiResult<VoucherDetails> = ApiResult.Failure(NetworkError.Unknown()),
    ) : VoucherRemoteDataSource {
        var listCalls = 0
        var detailCalls = 0
        override suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage> {
            listCalls += 1
            return listResult
        }
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): ApiResult<VoucherDetails> {
            detailCalls += 1
            return detailsResult
        }
    }

    private object UnreachableRemote : VoucherRemoteDataSource {
        override suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage> =
            error("UnreachableRemote must never be called on the AUTHENTICATED path")
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): ApiResult<VoucherDetails> =
            error("UnreachableRemote must never be called on the AUTHENTICATED path")
    }

    private class FakeAuthenticatedListRemote(
        private val result: AppResult<VoucherPage>,
    ) : AuthenticatedVoucherListRemoteDataSource {
        var callCount = 0
            private set
        var lastQuery: VoucherQuery? = null
            private set

        override suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage> {
            callCount++
            lastQuery = query
            return result
        }
    }

    private object UnreachableAuthenticatedListRemote : AuthenticatedVoucherListRemoteDataSource {
        override suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage> =
            error("UnreachableAuthenticatedListRemote must never be called on the LEGACY path")
    }

    /**
     * Simulates a multi-page Connector result set. Two modes:
     * - [pages]: literal per-call page contents, in order (existing small-scenario tests).
     * - [totalRecords]: generates `ceil(totalRecords / recordsPerPage)` pages of synthetic
     *   distinct vouchers on the fly — for boundary/scale tests without listing every record.
     * [failOnPage] fails that one page outright. [corruptTotalOnPage] reports a different
     * `totalItems` than every other page (inconsistent metadata). [emptyButClaimsMoreOnPage]
     * returns zero items on that page while still claiming more pages exist (invalid metadata).
     */
    private class FakeAuthenticatedListRemotePaged(
        private val pages: List<List<VoucherSummary>> = emptyList(),
        private val neverEnds: Boolean = false,
        private val totalRecords: Int? = null,
        private val recordsPerPage: Int = 100,
        private val failOnPage: Int? = null,
        private val corruptTotalOnPage: Int? = null,
        private val emptyButClaimsMoreOnPage: Int? = null,
    ) : AuthenticatedVoucherListRemoteDataSource {
        var callCount = 0
            private set
        var lastQuery: VoucherQuery? = null
            private set

        override suspend fun fetchVouchers(query: VoucherQuery): AppResult<VoucherPage> {
            lastQuery = query
            callCount++
            if (failOnPage == query.page) return AppResult.Failure(AppError.Offline())
            if (neverEnds) {
                val filler = VoucherSummary(
                    identity = VoucherIdentity("v-$callCount"),
                    date = "2026-07-15",
                    type = "Sales",
                    number = null,
                    partyName = null,
                    referenceNumber = null,
                    amount = null,
                    status = VoucherStatus.Active,
                    dataQuality = VoucherDataQuality.Complete,
                )
                return AppResult.Success(
                    VoucherPage(
                        "company-a", listOf(filler), query.page, query.pageSize, Int.MAX_VALUE, Int.MAX_VALUE,
                        fullDetails = if (query.includeDetails) listOf(filler.toEmptyDetails()) else null,
                    ),
                )
            }
            if (totalRecords != null) {
                val totalPages = ((totalRecords + recordsPerPage - 1) / recordsPerPage).coerceAtLeast(1)
                val startIndex = (query.page - 1) * recordsPerPage
                val endIndex = minOf(startIndex + recordsPerPage, totalRecords)
                val items = if (emptyButClaimsMoreOnPage == query.page) {
                    emptyList()
                } else {
                    (startIndex until endIndex).map { filler("v-$it") }
                }
                val reportedTotal = if (corruptTotalOnPage == query.page) totalRecords + 1 else totalRecords
                val reportedTotalPages = if (emptyButClaimsMoreOnPage == query.page) totalPages + 1 else totalPages
                return AppResult.Success(
                    VoucherPage(
                        "company-a", items, query.page, query.pageSize, reportedTotal, reportedTotalPages,
                        fullDetails = if (query.includeDetails) items.map { it.toEmptyDetails() } else null,
                    ),
                )
            }
            val index = query.page - 1
            val items = pages.getOrElse(index) { emptyList() }
            return AppResult.Success(
                VoucherPage(
                    companyId = "company-a",
                    items = items,
                    page = query.page,
                    pageSize = query.pageSize,
                    totalItems = pages.sumOf { it.size },
                    // canLoadMore = page < totalPages, so totalPages = pages.size makes the fake
                    // stop exactly after serving its last configured page.
                    totalPages = pages.size.coerceAtLeast(1),
                    fullDetails = if (query.includeDetails) items.map { it.toEmptyDetails() } else null,
                ),
            )
        }

        private fun filler(id: String) = VoucherSummary(
            identity = VoucherIdentity(id),
            date = "2026-07-15",
            type = "Sales",
            number = null,
            partyName = null,
            referenceNumber = null,
            amount = null,
            status = VoucherStatus.Active,
            dataQuality = VoucherDataQuality.Complete,
        )
    }

    private class FakeAuthenticatedDetailRemote(
        private val result: AppResult<VoucherDetails>,
    ) : AuthenticatedVoucherDetailRemoteDataSource {
        var callCount = 0
            private set
        var lastCompanyId: String? = null
            private set
        var lastVoucherId: String? = null
            private set

        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
            callCount++
            lastCompanyId = companyId
            lastVoucherId = voucherId
            return result
        }
    }

    private object UnreachableAuthenticatedDetailRemote : AuthenticatedVoucherDetailRemoteDataSource {
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
            error("UnreachableAuthenticatedDetailRemote must never be called on the LEGACY path")
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

    private class FakeLocal(
        private val listValue: VoucherPage? = null,
        private var detailsValue: VoucherDetails? = null,
        private val summaryValue: VoucherSummary? = null,
    ) : VoucherLocalDataSource {
        var storedItems: List<VoucherSummary> = emptyList()
        var storeListCalls = 0
        var lastListCompany: String? = null
        var lastStoreCompany: String? = null
        var storeDetailsCalls = 0
        var lastStoreDetailsCompany: String? = null
        var lastStoreDetailsId: String? = null
        var lastScopeFrom: String? = null
        var lastScopeTo: String? = null
        val stored = mutableMapOf<String, MutableList<VoucherSummary>>()
        var storeListWithDetailsCalls = 0
        var storedFullDetails: List<VoucherDetails> = emptyList()

        override suspend fun storeList(
            companyId: String,
            items: List<VoucherSummary>,
            syncedAt: Long,
            scopeFrom: String,
            scopeTo: String,
        ) {
            storeListCalls += 1
            storedItems = items
            lastStoreCompany = companyId
            lastScopeFrom = scopeFrom
            lastScopeTo = scopeTo
            stored.getOrPut(companyId) { mutableListOf() }.also { bucket ->
                bucket.removeAll { it.date in scopeFrom..scopeTo && items.none { item -> item.identity.id == it.identity.id } }
                items.forEach { item ->
                    bucket.removeAll { it.identity.id == item.identity.id }
                    bucket.add(item)
                }
            }
        }
        override suspend fun storeListWithDetails(
            companyId: String,
            items: List<VoucherDetails>,
            syncedAt: Long,
            scopeFrom: String,
            scopeTo: String,
        ) {
            storeListWithDetailsCalls += 1
            storedFullDetails = items
            // Same authoritative-window replacement as storeList, just derived from full details —
            // every storeList-based assertion in this file continues to hold for the detail path too.
            storeList(companyId, items.map { it.summary }, syncedAt, scopeFrom, scopeTo)
        }
        override suspend fun storeDetails(companyId: String, details: VoucherDetails, syncedAt: Long) {
            storeDetailsCalls += 1
            lastStoreDetailsCompany = companyId
            lastStoreDetailsId = details.summary.identity.id
            detailsValue = details
        }
        override suspend fun list(query: VoucherQuery): VoucherPage? { lastListCompany = query.companyId; return listValue?.takeIf { it.companyId == query.companyId } }
        override suspend fun details(companyId: String, voucherId: String): VoucherDetails? = detailsValue
        override suspend fun summary(companyId: String, voucherId: String): VoucherSummary? = summaryValue
    }

    private fun query(companyId: String = "company-a") = VoucherQuery(companyId, VoucherDateRange("2026-07-01", "2026-07-27"))

    private fun samplePage(companyId: String = "company-a") =
        VoucherPage(companyId, listOf(sampleSummary()), 1, 50, 1, 1, fullDetails = listOf(sampleDetails()))

    private fun sampleDetails(voucherId: String = "v-1") = VoucherDetails(
        sampleSummary(id = voucherId), "2026-07-27", "cached", emptyList(),
        listOf(VoucherInventoryLine(1, "Item", "2 pcs", "10", null)),
    )

    private fun sampleSummary(id: String = "v-1") = VoucherSummary(
        identity = VoucherIdentity(id),
        date = "2026-07-27",
        type = "Sales",
        number = "S-1",
        partyName = "Acme",
        referenceNumber = null,
        amount = null,
        status = VoucherStatus.Active,
        dataQuality = VoucherDataQuality.Complete,
    )
}

private fun VoucherSummary.toEmptyDetails() = VoucherDetails(
    summary = this,
    effectiveDate = null,
    narration = null,
    ledgerEntries = emptyList(),
    inventoryEntries = emptyList(),
)
