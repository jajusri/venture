package com.jajusri.venture.feature.voucher.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.voucher.data.remote.AuthenticatedVoucherDetailRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.AuthenticatedVoucherListRemoteDataSource
import com.jajusri.venture.feature.voucher.data.remote.VoucherRemoteDataSource
import com.jajusri.venture.feature.voucher.data.local.VoucherLocalDataSource
import com.jajusri.venture.feature.voucher.domain.model.VoucherCacheState
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the sole source of truth for [listVouchers]/[getVoucherDetails]: both read the
 * local cache immediately and never contact the Connector. [refreshVouchers]/
 * [refreshVoucherDetails] are the only operations that reach the network; they persist a
 * successful response and otherwise leave the existing cache untouched — a failed refresh
 * is reported as a failure and must never be interpreted as "no cached data".
 *
 * [refreshVouchers] resolves [ConnectorTransportSelectionGate] once per call and, on
 * AUTHENTICATED, routes through [AuthenticatedVoucherListRemoteDataSource] instead of the legacy
 * [VoucherRemoteDataSource] — never both, never falling back to legacy once AUTHENTICATED is
 * selected. [query]'s `companyId`/`dateRange` are the sole scope used for both dispatch and
 * persistence for the entire call: neither is ever re-read from any mutable store, so a company
 * or scope change elsewhere in the app while a refresh is in flight can never mislabel that
 * refresh's write. [refreshVoucherDetails] resolves the same gate independently (Phase 3S-D2) and
 * routes through [AuthenticatedVoucherDetailRemoteDataSource] on AUTHENTICATED — a separate
 * adapter boundary from the list one, since [voucherId]/[companyId] are this method's own
 * parameters (not read from [query]), matching [AuthenticatedConnectorOperation.GetVoucherById]'s
 * now-corrected `voucherId`+`companyId` contract.
 */
@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val remoteDataSource: VoucherRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val localDataSource: VoucherLocalDataSource,
    private val timeProvider: TimeProvider,
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedRemoteDataSource: AuthenticatedVoucherListRemoteDataSource,
    private val authenticatedDetailRemoteDataSource: AuthenticatedVoucherDetailRemoteDataSource,
) : VoucherRepository {

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            localDataSource.list(query)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Message(NO_CACHE_MESSAGE))
        }

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            // An explicit refresh always fetches the complete offline-ready window — every
            // synchronized Voucher must be immediately usable (list, detail, Share/PDF) with no
            // follow-up per-Voucher download, regardless of which screen triggered the refresh.
            val detailQuery = query.copy(includeDetails = true)
            val complete = when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> fetchCompleteWindow(detailQuery) { pageQuery ->
                    when (val result = remoteDataSource.fetchVouchers(pageQuery)) {
                        is ApiResult.Success -> AppResult.Success(result.data)
                        is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
                    }
                }
                ConnectorTransportSelection.AUTHENTICATED -> fetchCompleteWindow(detailQuery) { pageQuery ->
                    authenticatedRemoteDataSource.fetchVouchers(pageQuery)
                }
            }
            when (complete) {
                is AppResult.Success -> persistAndReturn(query, complete.value)
                is AppResult.Failure -> complete
            }
        }

    /**
     * An explicit refresh must be a COMPLETE authoritative extraction for [query]'s whole date
     * window before anything is persisted: [persistAndReturn] treats the result as the full
     * truth for that scope and prunes any previously-cached voucher in scope that's absent from
     * it. An INCOMPLETE remote window must never become pruning authority — completeness is
     * proven two independent ways before this returns success: (1) the endpoint's own pagination
     * metadata says there is no more (`canLoadMore == false`), AND (2) the number of items
     * actually accumulated matches the endpoint's own reported total. Either signal alone could
     * be wrong (inconsistent/buggy metadata, a stuck page repeating itself); both must agree.
     * Any inconsistency — a total that changes between pages, an empty page that still claims
     * more exist — fails closed rather than silently trusting a partial result. Fetches with
     * [REFRESH_FETCH_PAGE_SIZE] (independent of the caller's own display page size);
     * [MAX_REFRESH_PAGES] exists only as a sanity/runaway guard (not a normal operational limit)
     * and, like every other failure path here, fails closed without persisting anything. Always
     * unfiltered (companyId + dateRange only) regardless of any search/type/number/party filter
     * on the caller's own [query]: the fetched set becomes the pruning baseline for the whole
     * scope in [persistAndReturn], so a filtered fetch would wrongly delete every non-matching
     * (but otherwise valid) cached voucher in that window. The caller's filters are applied only
     * when reading back from Room afterward, not during this network fetch.
     */
    /**
     * [details] is non-null only when [VoucherQuery.includeDetails] was set — every page must
     * then carry a [VoucherPage.fullDetails] list matching that page's [VoucherPage.items] 1:1
     * (same source response, so always true unless a transport/mapping bug loses data in
     * flight); any mismatch fails the whole window closed rather than persisting a Voucher whose
     * detail data silently doesn't correspond to its summary.
     */
    private data class CompleteWindow(
        val items: List<VoucherSummary>,
        val details: List<VoucherDetails>?,
    )

    private suspend fun fetchCompleteWindow(
        query: VoucherQuery,
        fetchPage: suspend (VoucherQuery) -> AppResult<VoucherPage>,
    ): AppResult<CompleteWindow> {
        val accumulated = mutableListOf<VoucherSummary>()
        val detailsAccumulated = if (query.includeDetails) mutableListOf<VoucherDetails>() else null
        var page = 1
        var provenTotal: Int? = null
        while (true) {
            val pageQuery = VoucherQuery(
                companyId = query.companyId,
                dateRange = query.dateRange,
                page = page,
                pageSize = REFRESH_FETCH_PAGE_SIZE,
                includeDetails = query.includeDetails,
            )
            when (val result = fetchPage(pageQuery)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> {
                    val pageData = result.value
                    if (provenTotal != null && pageData.totalItems != provenTotal) {
                        return AppResult.Failure(AppError.Message(INCOMPLETE_WINDOW_MESSAGE))
                    }
                    provenTotal = pageData.totalItems
                    accumulated += pageData.items
                    if (detailsAccumulated != null) {
                        val pageDetails = pageData.fullDetails
                        if (pageDetails == null || pageDetails.size != pageData.items.size) {
                            return AppResult.Failure(AppError.Message(INCOMPLETE_WINDOW_MESSAGE))
                        }
                        detailsAccumulated += pageDetails
                    }
                    if (pageData.items.isEmpty() && pageData.canLoadMore) {
                        // A page claiming more pages exist while returning nothing is invalid
                        // pagination metadata, not proof of anything — never loop on it.
                        return AppResult.Failure(AppError.Message(INCOMPLETE_WINDOW_MESSAGE))
                    }
                    if (!pageData.canLoadMore) {
                        return if (accumulated.size == provenTotal) {
                            AppResult.Success(CompleteWindow(accumulated, detailsAccumulated))
                        } else {
                            AppResult.Failure(AppError.Message(INCOMPLETE_WINDOW_MESSAGE))
                        }
                    }
                    if (page >= MAX_REFRESH_PAGES) {
                        return AppResult.Failure(AppError.Message(WINDOW_TOO_LARGE_MESSAGE))
                    }
                    page += 1
                }
            }
        }
    }

    private suspend fun persistAndReturn(query: VoucherQuery, window: CompleteWindow): AppResult<VoucherPage> {
        val syncedAt = timeProvider.nowEpochMillis()
        val details = window.details
        if (details != null) {
            localDataSource.storeListWithDetails(
                query.companyId,
                details,
                syncedAt,
                query.dateRange.from,
                query.dateRange.to,
            )
        } else {
            localDataSource.storeList(
                query.companyId,
                window.items,
                syncedAt,
                query.dateRange.from,
                query.dateRange.to,
            )
        }
        val refreshed = localDataSource.list(query)
            ?: VoucherPage(query.companyId, emptyList(), query.page, query.pageSize, window.items.size, 1)
        return AppResult.Success(refreshed.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
    }

    override suspend fun getVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            localDataSource.details(companyId, voucherId)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Message(NO_CACHE_DETAILS_MESSAGE))
        }

    override suspend fun refreshVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> when (val result = remoteDataSource.fetchVoucherDetails(companyId, voucherId)) {
                    is ApiResult.Success -> persistDetailsAndReturn(companyId, result.data)
                    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
                }

                ConnectorTransportSelection.AUTHENTICATED -> when (
                    val result = authenticatedDetailRemoteDataSource.fetchVoucherDetails(companyId, voucherId)
                ) {
                    is AppResult.Success -> persistDetailsAndReturn(companyId, result.value)
                    is AppResult.Failure -> result
                }
            }
        }

    private suspend fun persistDetailsAndReturn(companyId: String, details: VoucherDetails): AppResult<VoucherDetails> {
        val syncedAt = timeProvider.nowEpochMillis()
        localDataSource.storeDetails(companyId, details, syncedAt)
        return AppResult.Success(details.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
    }

    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? =
        withContext(dispatchers.io) { localDataSource.summary(companyId, voucherId) }

    companion object {
        const val NO_CACHE_MESSAGE = "No offline data available. Connect to VENTURE Desktop and synchronize once."
        const val NO_CACHE_DETAILS_MESSAGE = "No offline data available for this voucher. Connect to VENTURE Desktop and synchronize once."
        private const val REFRESH_FETCH_PAGE_SIZE = 100
        // A sanity/runaway guard only (100,000 pages = up to 10,000,000 vouchers) — real windows
        // should never approach this; it exists to fail closed rather than loop forever if the
        // remote side is genuinely misbehaving, not to cap legitimate large windows.
        private const val MAX_REFRESH_PAGES = 100_000
        const val WINDOW_TOO_LARGE_MESSAGE =
            "This date range has too many vouchers to refresh at once. Narrow the range and try again."
        const val INCOMPLETE_WINDOW_MESSAGE =
            "Could not confirm a complete voucher list for this date range. Nothing was changed locally."
    }
}
