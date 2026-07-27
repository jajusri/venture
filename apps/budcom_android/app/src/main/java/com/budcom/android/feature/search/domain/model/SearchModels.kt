package com.budcom.android.feature.search.domain.model

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.search.domain.UniversalSearchDefaults
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherStatus

/**
 * User-entered search text before normalization.
 */
data class SearchQuery(
    val raw: String,
) {
    /**
     * Trimmed, length-capped query suitable for Connector calls.
     * Returns null when blank or shorter than [UniversalSearchDefaults.MIN_QUERY_LENGTH].
     */
    fun normalized(): String? {
        val trimmed = raw.trim()
        if (trimmed.length < UniversalSearchDefaults.MIN_QUERY_LENGTH) return null
        return trimmed.take(UniversalSearchDefaults.MAX_QUERY_LENGTH)
    }
}

/**
 * Deterministic section order for Universal Search.
 */
enum class SearchSection {
    Ledgers,
    StockItems,
    Vouchers,
}

/**
 * Presentation-safe search hit preserving entity type and stable identity.
 */
sealed interface SearchHit {
    val id: String
    val section: SearchSection

    data class Ledger(
        override val id: String,
        val name: String,
        val parentGroup: String?,
        val statusLabel: String,
    ) : SearchHit {
        override val section: SearchSection = SearchSection.Ledgers
    }

    data class StockItem(
        override val id: String,
        val name: String,
        val parentGroup: String?,
        val category: String?,
        val baseUnit: String?,
        val statusLabel: String,
    ) : SearchHit {
        override val section: SearchSection = SearchSection.StockItems
    }

    data class Voucher(
        override val id: String,
        val type: String,
        val number: String?,
        val date: String,
        val partyName: String?,
        val amount: VoucherMoney?,
        val status: VoucherStatus,
        val dataQuality: VoucherDataQuality,
    ) : SearchHit {
        override val section: SearchSection = SearchSection.Vouchers
    }
}

/**
 * Per-section outcome. Failures do not suppress successful sibling sections.
 */
sealed interface SearchSectionState {
    val section: SearchSection

    data class Success(
        override val section: SearchSection,
        val hits: List<SearchHit>,
        val totalItems: Int,
        val previewLimit: Int,
    ) : SearchSectionState {
        val hasMore: Boolean get() = totalItems > hits.size
        val showSeeAll: Boolean get() = hasMore
    }

    data class Failure(
        override val section: SearchSection,
        val error: AppError,
    ) : SearchSectionState

    data class Empty(
        override val section: SearchSection,
    ) : SearchSectionState
}

/**
 * Aggregated Universal Search result for a single normalized query.
 */
data class UniversalSearchResult(
    val query: String,
    val sections: List<SearchSectionState>,
    val voucherDateRange: VoucherDateRange,
) {
    init {
        require(sections.map { it.section } == SearchSection.entries) {
            "Sections must appear in deterministic order: Ledgers, StockItems, Vouchers"
        }
    }

    val hasAnyHits: Boolean
        get() = sections.any { it is SearchSectionState.Success && it.hits.isNotEmpty() }

    val hasAnyFailure: Boolean
        get() = sections.any { it is SearchSectionState.Failure }

    val allEmpty: Boolean
        get() = sections.all { it is SearchSectionState.Empty }

    val isPartialSuccess: Boolean
        get() = hasAnyHits && hasAnyFailure
}
