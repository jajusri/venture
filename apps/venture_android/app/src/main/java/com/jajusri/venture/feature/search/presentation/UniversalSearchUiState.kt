package com.jajusri.venture.feature.search.presentation

import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.masterdata.presentation.toMasterDataUiError
import com.jajusri.venture.feature.search.domain.model.SearchHit
import com.jajusri.venture.feature.search.domain.model.SearchSection
import com.jajusri.venture.feature.search.domain.model.SearchSectionState
import com.jajusri.venture.feature.search.domain.model.UniversalSearchResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherMoney
import com.jajusri.venture.feature.voucher.domain.model.VoucherMoneySide
import com.jajusri.venture.feature.voucher.domain.model.VoucherStatus

/**
 * Presentation state for Universal Search. Contains no DTOs or domain repositories.
 */
data class UniversalSearchUiState(
    val query: String = "",
    val isOnline: Boolean = true,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val activeQuery: String? = null,
    val voucherDateFrom: String? = null,
    val voucherDateTo: String? = null,
    val sections: List<SearchSectionUi> = emptyList(),
) {
    val showIdleHint: Boolean
        get() = !hasSearched && !isSearching && query.isBlank()

    val showEmpty: Boolean
        get() = hasSearched && !isSearching && sections.all { it is SearchSectionUi.Empty }

    val isPartialFailure: Boolean
        get() = sections.any { it is SearchSectionUi.Success } &&
            sections.any { it is SearchSectionUi.Failure }

    val allFailed: Boolean
        get() = hasSearched && !isSearching &&
            sections.isNotEmpty() &&
            sections.all { it is SearchSectionUi.Failure }
}

sealed interface SearchSectionUi {
    val section: SearchSection

    data class Loading(override val section: SearchSection) : SearchSectionUi

    data class Success(
        override val section: SearchSection,
        val rows: List<SearchResultRowUi>,
        val totalItems: Int,
        val showSeeAll: Boolean,
    ) : SearchSectionUi

    data class Empty(override val section: SearchSection) : SearchSectionUi

    data class Failure(
        override val section: SearchSection,
        val error: MasterDataUiError,
    ) : SearchSectionUi
}

data class SearchResultRowUi(
    val id: String,
    val section: SearchSection,
    val primary: String,
    val secondary: String?,
)

sealed interface UniversalSearchEvent {
    data class QueryChanged(val query: String) : UniversalSearchEvent
    data object ClearQuery : UniversalSearchEvent
    data object Retry : UniversalSearchEvent
    data class RetrySection(val section: SearchSection) : UniversalSearchEvent
    data class ResultClicked(val row: SearchResultRowUi) : UniversalSearchEvent
    data class SeeAll(val section: SearchSection) : UniversalSearchEvent
}

sealed interface UniversalSearchNavigation {
    data class LedgerBrowser(val query: String) : UniversalSearchNavigation
    data class StockItemBrowser(val query: String) : UniversalSearchNavigation
    data class VoucherBrowser(val query: String) : UniversalSearchNavigation
    data class VoucherDetails(val voucherId: String) : UniversalSearchNavigation
}

internal fun UniversalSearchResult.toUiSections(): List<SearchSectionUi> =
    sections.map { it.toUi() }

internal fun SearchSectionState.toUi(): SearchSectionUi = when (this) {
    is SearchSectionState.Empty -> SearchSectionUi.Empty(section)
    is SearchSectionState.Failure -> SearchSectionUi.Failure(section, error.toMasterDataUiError())
    is SearchSectionState.Success -> SearchSectionUi.Success(
        section = section,
        rows = hits.map { it.toRow() },
        totalItems = totalItems,
        showSeeAll = showSeeAll,
    )
}

internal fun SearchHit.toRow(): SearchResultRowUi = when (this) {
    is SearchHit.Ledger -> SearchResultRowUi(
        id = id,
        section = SearchSection.Ledgers,
        primary = name,
        secondary = listOfNotNull(
            parentGroup?.takeIf { it.isNotBlank() },
            statusLabel,
        ).joinToString(" · ").ifBlank { null },
    )
    is SearchHit.StockItem -> SearchResultRowUi(
        id = id,
        section = SearchSection.StockItems,
        primary = name,
        secondary = listOfNotNull(
            parentGroup?.takeIf { it.isNotBlank() },
            category?.takeIf { it.isNotBlank() },
            baseUnit?.takeIf { it.isNotBlank() }?.let { "Unit: $it" },
            statusLabel,
        ).joinToString(" · ").ifBlank { null },
    )
    is SearchHit.Voucher -> SearchResultRowUi(
        id = id,
        section = SearchSection.Vouchers,
        primary = listOfNotNull(type, number).joinToString(" · ").ifBlank { type },
        secondary = listOfNotNull(
            date,
            partyName?.takeIf { it.isNotBlank() },
            amount?.toDisplay(),
            status.toLabel(),
        ).joinToString(" · ").ifBlank { null },
    )
}

private fun VoucherMoney.toDisplay(): String {
    val side = when (side) {
        VoucherMoneySide.Debit -> " Dr"
        VoucherMoneySide.Credit -> " Cr"
        null -> ""
    }
    return "$value$side"
}

private fun VoucherStatus.toLabel(): String = when (this) {
    VoucherStatus.Active -> "Active"
    VoucherStatus.Cancelled -> "Cancelled"
    VoucherStatus.Unknown -> "Unknown"
}

internal fun loadingSections(): List<SearchSectionUi> =
    SearchSection.entries.map { SearchSectionUi.Loading(it) }
