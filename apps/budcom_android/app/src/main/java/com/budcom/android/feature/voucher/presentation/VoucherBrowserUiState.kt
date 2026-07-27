package com.budcom.android.feature.voucher.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherSummary

data class VoucherBrowserUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val companyId: String? = null,
    val dateFrom: String = DefaultDateRange.from,
    val dateTo: String = DefaultDateRange.to,
    val searchQuery: String = "",
    val vouchers: List<VoucherRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 50,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val canLoadMore: Boolean = false,
    val isOnline: Boolean = true,
    val error: MasterDataUiError? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = vouchers.isNotEmpty()
    val hasCompany: Boolean get() = !companyId.isNullOrBlank()

    private object DefaultDateRange {
        private val range = VoucherDateRangeDefaults.lastDaysInclusive()
        val from: String = range.from
        val to: String = range.to
    }
}

data class VoucherRowUi(
    val id: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val dateLabel: String,
    val typeLabel: String,
    val statusLabel: String,
    val amountLabel: String?,
)

sealed interface VoucherBrowserEvent {
    data object Load : VoucherBrowserEvent
    data object Refresh : VoucherBrowserEvent
    data object Retry : VoucherBrowserEvent
    data object LoadNextPage : VoucherBrowserEvent
    data class SearchChanged(val query: String) : VoucherBrowserEvent
    data class DateFromChanged(val value: String) : VoucherBrowserEvent
    data class DateToChanged(val value: String) : VoucherBrowserEvent
    data object ApplyDateRange : VoucherBrowserEvent
}

internal fun AppError.toVoucherUiError(): MasterDataUiError = toMasterDataUiError()

internal fun VoucherSummary.toRowUi(): VoucherRowUi {
    val numberLabel = number?.takeIf { it.isNotBlank() } ?: "—"
    val secondary = listOfNotNull(
        partyName,
        referenceNumber?.let { "Ref: $it" },
    ).joinToString(" · ").ifBlank { null }
    val amount = amount?.let { money ->
        val side = money.side?.name?.lowercase()
        if (side != null) "${money.value} ($side)" else money.value
    }
    return VoucherRowUi(
        id = identity.id,
        primaryLabel = numberLabel,
        secondaryLabel = secondary,
        dateLabel = date,
        typeLabel = type,
        statusLabel = status.name.lowercase().replaceFirstChar { it.titlecase() },
        amountLabel = amount,
    )
}

internal fun VoucherPage.toRows(): List<VoucherRowUi> = items.map { it.toRowUi() }

internal fun tryParseDateRange(from: String, to: String): VoucherDateRange? =
    runCatching { VoucherDateRange(from = from.trim(), to = to.trim()) }.getOrNull()
