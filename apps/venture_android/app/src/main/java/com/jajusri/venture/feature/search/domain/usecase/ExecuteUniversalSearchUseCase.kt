package com.jajusri.venture.feature.search.domain.usecase

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatus
import com.jajusri.venture.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.jajusri.venture.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.jajusri.venture.feature.search.domain.UniversalSearchDefaults
import com.jajusri.venture.feature.search.domain.model.SearchHit
import com.jajusri.venture.feature.search.domain.model.SearchQuery
import com.jajusri.venture.feature.search.domain.model.SearchSection
import com.jajusri.venture.feature.search.domain.model.SearchSectionState
import com.jajusri.venture.feature.search.domain.model.UniversalSearchResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRange
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRangeDefaults
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.port.SearchVouchersPort
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.time.Clock
import javax.inject.Inject

/**
 * Orchestrates typed entity search ports into a grouped Universal Search result.
 *
 * Does not invent a Connector universal-search endpoint. Section failures are independent.
 */
class ExecuteUniversalSearchUseCase @Inject constructor(
    private val searchLedgers: SearchLedgersPort,
    private val searchStockItems: SearchStockItemsPort,
    private val searchVouchers: SearchVouchersPort,
    private val clock: Clock,
) {
    /**
     * @return null when the query is blank / below minimum length (caller must not search).
     */
    suspend operator fun invoke(
        query: SearchQuery,
        companyId: String?,
        previewPageSize: Int = UniversalSearchDefaults.PREVIEW_PAGE_SIZE,
    ): UniversalSearchResult? {
        val normalized = query.normalized() ?: return null
        val limit = previewPageSize.coerceIn(1, 100)
        val voucherRange = VoucherDateRangeDefaults.lastDaysInclusive(
            days = UniversalSearchDefaults.VOUCHER_LOOKBACK_DAYS,
            clock = clock,
        )

        return supervisorScope {
            val ledgersDeferred = async {
                searchLedgers.search(
                    LedgerQuery(text = normalized, page = 1, pageSize = limit),
                )
            }
            val stockDeferred = async {
                searchStockItems.search(
                    StockItemQuery(text = normalized, page = 1, pageSize = limit),
                )
            }
            val vouchersDeferred = async {
                searchVoucherSection(companyId, normalized, limit, voucherRange)
            }

            UniversalSearchResult(
                query = normalized,
                sections = listOf(
                    mapLedgerResult(ledgersDeferred.await(), limit),
                    mapStockResult(stockDeferred.await(), limit),
                    vouchersDeferred.await(),
                ),
                voucherDateRange = voucherRange,
            )
        }
    }

    private suspend fun searchVoucherSection(
        companyId: String?,
        normalized: String,
        limit: Int,
        dateRange: VoucherDateRange,
    ): SearchSectionState {
        if (companyId.isNullOrBlank()) {
            return SearchSectionState.Failure(
                section = SearchSection.Vouchers,
                error = AppError.Message("Select a company before searching vouchers."),
            )
        }
        return mapVoucherResult(
            searchVouchers.search(
                VoucherQuery(
                    companyId = companyId,
                    dateRange = dateRange,
                    searchText = normalized,
                    page = 1,
                    pageSize = limit,
                ),
            ),
            limit,
        )
    }

    private fun mapLedgerResult(result: AppResult<LedgerPage>, limit: Int): SearchSectionState =
        when (result) {
            is AppResult.Failure -> SearchSectionState.Failure(SearchSection.Ledgers, result.error)
            is AppResult.Success -> {
                val hits = result.value.items.map { it.toHit() }
                if (hits.isEmpty()) {
                    SearchSectionState.Empty(SearchSection.Ledgers)
                } else {
                    SearchSectionState.Success(
                        section = SearchSection.Ledgers,
                        hits = hits,
                        totalItems = result.value.totalItems,
                        previewLimit = limit,
                    )
                }
            }
        }

    private fun mapStockResult(result: AppResult<StockItemPage>, limit: Int): SearchSectionState =
        when (result) {
            is AppResult.Failure -> SearchSectionState.Failure(SearchSection.StockItems, result.error)
            is AppResult.Success -> {
                val hits = result.value.items.map { it.toHit() }
                if (hits.isEmpty()) {
                    SearchSectionState.Empty(SearchSection.StockItems)
                } else {
                    SearchSectionState.Success(
                        section = SearchSection.StockItems,
                        hits = hits,
                        totalItems = result.value.pagination.totalItems,
                        previewLimit = limit,
                    )
                }
            }
        }

    private fun mapVoucherResult(result: AppResult<VoucherPage>, limit: Int): SearchSectionState =
        when (result) {
            is AppResult.Failure -> SearchSectionState.Failure(SearchSection.Vouchers, result.error)
            is AppResult.Success -> {
                val hits = result.value.items.map { it.toHit() }
                if (hits.isEmpty()) {
                    SearchSectionState.Empty(SearchSection.Vouchers)
                } else {
                    SearchSectionState.Success(
                        section = SearchSection.Vouchers,
                        hits = hits,
                        totalItems = result.value.totalItems,
                        previewLimit = limit,
                    )
                }
            }
        }
}

private fun Ledger.toHit(): SearchHit.Ledger = SearchHit.Ledger(
    id = id,
    name = name,
    parentGroup = parentGroup,
    statusLabel = status.toLabel(),
)

private fun StockItem.toHit(): SearchHit.StockItem = SearchHit.StockItem(
    id = id,
    name = name,
    parentGroup = parentGroup,
    category = category,
    baseUnit = baseUnit,
    statusLabel = status.toLabel(),
)

private fun VoucherSummary.toHit(): SearchHit.Voucher = SearchHit.Voucher(
    id = identity.id,
    type = type,
    number = number,
    date = date,
    partyName = partyName,
    amount = amount,
    status = status,
    dataQuality = dataQuality,
)

private fun LedgerStatus.toLabel(): String = when (this) {
    LedgerStatus.Active -> "Active"
    LedgerStatus.Inactive -> "Inactive"
    LedgerStatus.Reserved -> "Reserved"
    LedgerStatus.Unknown -> "Unknown"
}

private fun StockItemStatus.toLabel(): String = when (this) {
    StockItemStatus.Active -> "Active"
    StockItemStatus.Inactive -> "Inactive"
    StockItemStatus.Unknown -> "Unknown"
}
