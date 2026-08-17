package com.budcom.android.feature.connect.presentation

import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.party.domain.model.PartyClassification

/** Connect's two primary sections (architecture §11/§29 — Customer/Prospect only in the UI). */
enum class ConnectTab {
    Customers,
    Prospects,
}

internal fun ConnectTab.toClassification(): PartyClassification = when (this) {
    ConnectTab.Customers -> PartyClassification.Customer
    ConnectTab.Prospects -> PartyClassification.Prospect
}

data class ConnectUiState(
    val companyId: String? = null,
    val selectedTab: ConnectTab = ConnectTab.Customers,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val rows: List<ConnectRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = MasterDataBrowserDefaults.DEFAULT_PAGE_SIZE,
    val totalItems: Int = 0,
    val canLoadMore: Boolean = false,
    val isOnline: Boolean = true,
    val error: MasterDataUiError? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = rows.isNotEmpty()
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

/**
 * One Connect row. [balanceLabel]/[linkedLedgerId] are null for a BUDCOM-only Party (no accounting
 * source link) — never fabricated (architecture §4/§12). [phoneE164] is the strictly-validated
 * form used for Call/WhatsApp actions; [phoneDisplay] preserves the original value shown to the
 * user, which may differ (e.g. a phone that fails strict validation still displays but cannot be
 * dialed directly).
 */
data class ConnectRowUi(
    val partyId: String,
    val displayName: String,
    val phoneDisplay: String?,
    val phoneE164: String?,
    val tagNames: List<String>,
    val balanceLabel: String?,
    val linkedLedgerId: String?,
    val linkedLedgerName: String?,
) {
    val hasAccountingLink: Boolean get() = linkedLedgerId != null
}

sealed interface ConnectEffect {
    data class OpenLedgerStatement(val ledgerId: String) : ConnectEffect
    data class OpenVouchers(val query: String) : ConnectEffect
    data class LaunchCall(val phoneE164: String) : ConnectEffect
    data class LaunchWhatsApp(val phoneE164: String) : ConnectEffect
    data class ShowMessage(val message: String) : ConnectEffect
}

sealed interface ConnectEvent {
    data object Load : ConnectEvent
    data object Refresh : ConnectEvent
    data object Retry : ConnectEvent
    data object LoadNextPage : ConnectEvent
    data class TabChanged(val tab: ConnectTab) : ConnectEvent
    data class SearchChanged(val query: String) : ConnectEvent
    data class ViewLedgerTapped(val ledgerId: String?) : ConnectEvent
    data class ViewVouchersTapped(val ledgerName: String) : ConnectEvent
    data class CallTapped(val phoneE164: String?) : ConnectEvent
    data class WhatsAppTapped(val phoneE164: String?) : ConnectEvent
}
