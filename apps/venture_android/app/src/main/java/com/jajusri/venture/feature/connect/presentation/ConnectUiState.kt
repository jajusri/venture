package com.jajusri.venture.feature.connect.presentation

import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.party.domain.model.PartyClassification

/** Connect's primary sections. Originally Customer/Prospect only (architecture §11/§29); Creditors
 * (Supplier classification, already reconciled from eligible Tally ledgers — see
 * `LedgerPartyEligibilityPolicy`) added as a third tab on explicit user request. */
enum class ConnectTab {
    Customers,
    Prospects,
    Creditors,
}

internal fun ConnectTab.toClassification(): PartyClassification = when (this) {
    ConnectTab.Customers -> PartyClassification.Customer
    ConnectTab.Prospects -> PartyClassification.Prospect
    ConnectTab.Creditors -> PartyClassification.Supplier
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
    /**
     * Freshness of the underlying local Ledger data Connect's balances/deep-links are derived
     * from (the most recent [com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
     * .syncedAt] across the currently-cached company) -- Connect has no independent "synced at"
     * concept of its own since Parties are reconciled from Ledgers, not synced directly. Mirrors
     * Ledger Browser's own `dataFreshnessAt` display so the two screens read consistently; null
     * only when no Ledger data has ever been cached for this company yet.
     */
    val dataFreshnessAt: String? = null,
    /** True while the manually-triggered bulk contact-details fetch (address/email/GSTIN
     * auto-population) is in flight -- see [ConnectEvent.FetchContactDetailsTapped]. Deliberately
     * NOT part of [isBusy]/the initial-loading/refresh states below, since this is a distinct,
     * user-initiated, occasional action, not part of the row-loading lifecycle. */
    val isFetchingContactDetails: Boolean = false,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = rows.isNotEmpty()
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

/**
 * One Connect row. [balanceLabel]/[linkedLedgerId] are null for a VENTURE-only Party (no accounting
 * source link) — never fabricated (architecture §4/§12). [phoneE164] is the strictly-validated
 * form used for Call/WhatsApp actions; [phoneDisplay] preserves the original value shown to the
 * user, which may differ (e.g. a phone that fails strict validation still displays but cannot be
 * dialed directly). [linkedLedgerAlias] is the linked Tally Ledger's raw Alias, shown separately
 * from [phoneDisplay] and always explicitly labeled ("Alias: ...", mirroring Ledger Browser's own
 * convention) so a short numeric Alias — which may have surfaced this very Party via the 1-5 digit
 * search shortcut — is never mistaken for a phone number; null whenever no linked Ledger has an
 * Alias, which is most of the time.
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
    val linkedLedgerAlias: String? = null,
) {
    val hasAccountingLink: Boolean get() = linkedLedgerId != null
}

sealed interface ConnectEffect {
    data class OpenLedgerStatement(val ledgerId: String) : ConnectEffect
    data class OpenVouchers(val query: String) : ConnectEffect
    data class OpenPartyDetail(val partyId: String) : ConnectEffect
    data object OpenProspectCreate : ConnectEffect
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
    data class RowTapped(val partyId: String) : ConnectEvent
    data object AddProspectTapped : ConnectEvent
    data class ViewLedgerTapped(val ledgerId: String?) : ConnectEvent
    data class ViewVouchersTapped(val ledgerName: String) : ConnectEvent
    data class CallTapped(val phoneE164: String?) : ConnectEvent
    data class WhatsAppTapped(val phoneE164: String?) : ConnectEvent
    /** Manual, occasional bulk fetch of Tally mailing/contact/GST fields for every ledger, seeded
     * into matching Parties' address/email/GSTIN -- see `LedgerBulkContactDetailPort`. Never
     * triggered automatically. */
    data object FetchContactDetailsTapped : ConnectEvent
}
